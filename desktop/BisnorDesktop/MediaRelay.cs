using System;
using System.IO;
using System.Net;
using System.Net.Http;
using System.Net.Sockets;
using System.Text;
using System.Threading;
using System.Threading.Tasks;

namespace BisnorDesktop;

/// <summary>
/// Streams plain-HTTP catalogue media over a loopback HTTP endpoint.
/// The shell page is served from https://appassets.bisnor, and Chromium blocks
/// the http CDN links as mixed content. Loopback addresses are treated as
/// trustworthy, so the page may load them and keep playing MKV through the
/// in-page remuxer.
/// </summary>
/// <remarks>
/// Implemented on a raw <see cref="TcpListener"/> because <c>HttpListener</c>
/// needs an HTTP.SYS URL reservation that a normal desktop user cannot create.
/// </remarks>
internal sealed class MediaRelay : IDisposable
{
    private readonly TcpListener _listener;
    private readonly HttpClient _client;
    private readonly string _tokenPath;
    private readonly CancellationTokenSource _shutdown = new();
    private bool _running;

    public string Origin { get; }

    public MediaRelay()
    {
        // Port 0 lets the OS pick a free loopback port.
        _listener = new TcpListener(IPAddress.Loopback, 0);
        _listener.Start();
        var port = ((IPEndPoint)_listener.LocalEndpoint).Port;
        // A random path segment keeps other local pages from using the relay.
        _tokenPath = $"/{Guid.NewGuid():N}/media";
        Origin = $"http://127.0.0.1:{port}";

        _client = new HttpClient(new HttpClientHandler
        {
            AllowAutoRedirect = true,
            AutomaticDecompression = DecompressionMethods.None,
            // The media CDNs are domestic; a system proxy (common on Iranian
            // laptops) would route playback through an unreachable endpoint.
            UseProxy = false
        })
        {
            Timeout = TimeSpan.FromMinutes(5)
        };
        _client.DefaultRequestHeaders.UserAgent.ParseAdd("Mozilla/5.0 (Windows NT 10.0; Win64; x64)");
    }

    /// <summary>URL prefix the web layer appends <c>?u=&lt;encoded media url&gt;</c> to.</summary>
    public string MediaPath => _tokenPath;

    public void Start()
    {
        _running = true;
        _ = Task.Run(AcceptLoopAsync);
    }

    private async Task AcceptLoopAsync()
    {
        while (_running)
        {
            TcpClient client;
            try
            {
                client = await _listener.AcceptTcpClientAsync(_shutdown.Token);
            }
            catch
            {
                return;
            }
            _ = Task.Run(() => HandleClientAsync(client));
        }
    }

    private async Task HandleClientAsync(TcpClient client)
    {
        using (client)
        {
            try
            {
                client.ReceiveTimeout = 15000;
                client.SendTimeout = 30000;
                using var stream = client.GetStream();
                var head = await ReadHeadAsync(stream);
                if (head == null) return;

                var lines = head.Split("\r\n");
                var parts = lines[0].Split(' ');
                if (parts.Length < 3) return;
                var method = parts[0].ToUpperInvariant();
                var target = parts[1];

                if (method == "OPTIONS")
                {
                    await WriteHeadAsync(stream, 204, "No Content", -1, null);
                    return;
                }
                if (method != "GET" && method != "HEAD")
                {
                    await WriteHeadAsync(stream, 405, "Method Not Allowed", 0, null);
                    return;
                }

                var path = target;
                var query = "";
                var q = target.IndexOf('?');
                if (q >= 0)
                {
                    path = target[..q];
                    query = target[(q + 1)..];
                }
                if (!string.Equals(path, _tokenPath, StringComparison.Ordinal))
                {
                    await WriteHeadAsync(stream, 403, "Forbidden", 0, null);
                    return;
                }

                var raw = ReadQueryValue(query, "u");
                if (string.IsNullOrWhiteSpace(raw)
                    || !Uri.TryCreate(raw, UriKind.Absolute, out var upstreamUri)
                    || (upstreamUri.Scheme != Uri.UriSchemeHttp && upstreamUri.Scheme != Uri.UriSchemeHttps))
                {
                    await WriteHeadAsync(stream, 400, "Bad Request", 0, null);
                    return;
                }

                using var upstreamRequest = new HttpRequestMessage(
                    method == "HEAD" ? HttpMethod.Head : HttpMethod.Get, upstreamUri);
                var range = ReadHeader(lines, "Range");
                if (!string.IsNullOrEmpty(range))
                {
                    upstreamRequest.Headers.TryAddWithoutValidation("Range", range);
                }
                var ifRange = ReadHeader(lines, "If-Range");
                if (!string.IsNullOrEmpty(ifRange))
                {
                    upstreamRequest.Headers.TryAddWithoutValidation("If-Range", ifRange);
                }

                using var upstream = await _client.SendAsync(upstreamRequest, HttpCompletionOption.ResponseHeadersRead);
                var length = upstream.Content.Headers.ContentLength ?? -1;
                await WriteHeadAsync(stream, (int)upstream.StatusCode, upstream.ReasonPhrase ?? "OK", length, upstream);

                if (method == "GET" && upstream.Content != null)
                {
                    await using var body = await upstream.Content.ReadAsStreamAsync();
                    await body.CopyToAsync(stream, 65536);
                }
            }
            catch
            {
                // The player cancels range requests routinely; nothing to report.
            }
        }
    }

    private static async Task<string?> ReadHeadAsync(NetworkStream stream)
    {
        var buffer = new byte[8192];
        var text = new StringBuilder();
        while (text.Length < 65536)
        {
            var read = await stream.ReadAsync(buffer);
            if (read <= 0) return null;
            text.Append(Encoding.ASCII.GetString(buffer, 0, read));
            if (text.ToString().Contains("\r\n\r\n", StringComparison.Ordinal))
            {
                return text.ToString();
            }
        }
        return null;
    }

    private static string? ReadHeader(string[] lines, string name)
    {
        for (var i = 1; i < lines.Length; i++)
        {
            var colon = lines[i].IndexOf(':');
            if (colon <= 0) continue;
            if (string.Equals(lines[i][..colon].Trim(), name, StringComparison.OrdinalIgnoreCase))
            {
                return lines[i][(colon + 1)..].Trim();
            }
        }
        return null;
    }

    private static string? ReadQueryValue(string query, string key)
    {
        foreach (var pair in query.Split('&'))
        {
            var eq = pair.IndexOf('=');
            if (eq <= 0) continue;
            if (string.Equals(pair[..eq], key, StringComparison.Ordinal))
            {
                return Uri.UnescapeDataString(pair[(eq + 1)..]);
            }
        }
        return null;
    }

    private static async Task WriteHeadAsync(NetworkStream stream, int status, string reason, long length, HttpResponseMessage? upstream)
    {
        var head = new StringBuilder();
        head.Append("HTTP/1.1 ").Append(status).Append(' ').Append(reason).Append("\r\n");
        head.Append("Access-Control-Allow-Origin: *\r\n");
        head.Append("Access-Control-Allow-Headers: range, if-range, accept\r\n");
        head.Append("Access-Control-Allow-Methods: GET, HEAD, OPTIONS\r\n");
        head.Append("Access-Control-Expose-Headers: content-length, content-range, accept-ranges\r\n");
        head.Append("Access-Control-Allow-Private-Network: true\r\n");
        head.Append("Connection: close\r\n");
        if (length >= 0) head.Append("Content-Length: ").Append(length).Append("\r\n");
        if (upstream != null)
        {
            AppendHeader(head, upstream, "Content-Type");
            AppendHeader(head, upstream, "Content-Range");
            AppendHeader(head, upstream, "Accept-Ranges");
            AppendHeader(head, upstream, "Last-Modified");
            AppendHeader(head, upstream, "ETag");
        }
        head.Append("\r\n");
        var bytes = Encoding.ASCII.GetBytes(head.ToString());
        await stream.WriteAsync(bytes);
        await stream.FlushAsync();
    }

    private static void AppendHeader(StringBuilder head, HttpResponseMessage upstream, string name)
    {
        if (!upstream.Headers.TryGetValues(name, out var values)
            && !upstream.Content.Headers.TryGetValues(name, out values))
        {
            return;
        }
        head.Append(name).Append(": ").Append(string.Join(", ", values)).Append("\r\n");
    }

    public void Dispose()
    {
        _running = false;
        _shutdown.Cancel();
        try { _listener.Stop(); } catch { /* already stopped */ }
        _client.Dispose();
        _shutdown.Dispose();
    }
}

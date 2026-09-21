using System;
using System.Diagnostics;
using System.IO;
using System.Linq;
using System.Net.Http;
using System.Text.Json;
using System.Threading.Tasks;
using System.Windows;
using Microsoft.Web.WebView2.Core;

namespace BisnorDesktop;

public partial class MainWindow : Window
{
    private static readonly string[] IranflixServers = new[]
    {
        "https://server-hi-speed-iran.info",
        "https://hostinnegar.com",
        "https://windowsdiba.info"
    };
    private const string ApiKey = "4F5A9C3D9A86FA54EACEDDD635185";

    private readonly HttpClient _httpClient = new(new HttpClientHandler
    {
        AllowAutoRedirect = true
    })
    {
        Timeout = TimeSpan.FromSeconds(15)
    };

    public MainWindow()
    {
        var appVersion = typeof(MainWindow).Assembly.GetName().Version?.ToString(3) ?? "5.1.1";
        _httpClient.DefaultRequestHeaders.UserAgent.ParseAdd($"Mozilla/5.0 (Windows NT 10.0; Win64; x64) BisnorDesktop/{appVersion}");
        InitializeComponent();

        try
        {
            var icoPath = Path.Combine(AppDomain.CurrentDomain.BaseDirectory, "wwwroot", "assets", "logo.ico");
            if (File.Exists(icoPath))
            {
                Icon = new System.Windows.Media.Imaging.BitmapImage(new Uri(icoPath));
            }
        }
        catch { }

        Loaded += MainWindow_Loaded;
        ContentRendered += (s, e) =>
        {
            Activate();
            Focus();
        };
    }

    private async void MainWindow_Loaded(object sender, RoutedEventArgs e)
    {
        try
        {
            var userDataFolder = Path.Combine(
                Environment.GetFolderPath(Environment.SpecialFolder.LocalApplicationData),
                "BisnorDesktop",
                "WebView2Data"
            );
            Directory.CreateDirectory(userDataFolder);

            var env = await CoreWebView2Environment.CreateAsync(null, userDataFolder);
            await webView.EnsureCoreWebView2Async(env);

            webView.CoreWebView2.Settings.IsStatusBarEnabled = false;
            webView.CoreWebView2.Settings.AreDevToolsEnabled = true;
            webView.CoreWebView2.Settings.IsZoomControlEnabled = false;
            webView.CoreWebView2.Settings.IsPinchZoomEnabled = false;

            webView.CoreWebView2.WebMessageReceived += CoreWebView2_WebMessageReceived;

            var baseDir = AppDomain.CurrentDomain.BaseDirectory;
            var searchPaths = new[]
            {
                Path.Combine(baseDir, "wwwroot", "index.html"),
                Path.GetFullPath(Path.Combine(baseDir, "..", "..", "..", "wwwroot", "index.html")),
                Path.GetFullPath(Path.Combine(baseDir, "..", "BisnorDesktop", "wwwroot", "index.html")),
                Path.GetFullPath(Path.Combine(AppContext.BaseDirectory, "wwwroot", "index.html"))
            };

            string? foundIndexPath = null;
            foreach (var p in searchPaths)
            {
                if (File.Exists(p))
                {
                    foundIndexPath = p;
                    break;
                }
            }

            if (foundIndexPath != null)
            {
                var wwwrootDir = Path.GetDirectoryName(foundIndexPath)!;
                webView.CoreWebView2.SetVirtualHostNameToFolderMapping(
                    "appassets.bisnor",
                    wwwrootDir,
                    CoreWebView2HostResourceAccessKind.Allow
                );
                webView.CoreWebView2.Navigate("https://appassets.bisnor/index.html");
            }
            else
            {
                MessageBox.Show($"فایل رابط کاربری بیسنور (index.html) در مسیرهای زیر یافت نشد:\n{string.Join("\n", searchPaths)}", "خطای بیسنور", MessageBoxButton.OK, MessageBoxImage.Warning);
            }
        }
        catch (Exception ex)
        {
            MessageBox.Show($"خطا در راه‌اندازی مرورگر داخلی بیسنور:\n{ex.Message}", "بیسنور دسکتاپ", MessageBoxButton.OK, MessageBoxImage.Error);
        }
    }

    private async void CoreWebView2_WebMessageReceived(object? sender, CoreWebView2WebMessageReceivedEventArgs e)
    {
        try
        {
            var rawJson = e.WebMessageAsJson;
            using var doc = JsonDocument.Parse(rawJson);
            var root = doc.RootElement;
            var action = root.GetProperty("action").GetString();

            switch (action)
            {
                case "launchExternalPlayer":
                    var playerType = root.GetProperty("player").GetString();
                    var streamUrl = root.GetProperty("url").GetString();
                    var title = root.TryGetProperty("title", out var t) ? t.GetString() : "Bisnor Media";
                    LaunchPlayer(playerType, streamUrl, title);
                    break;

                case "downloadWithIDM":
                    var downloadUrl = root.GetProperty("url").GetString();
                    LaunchIDM(downloadUrl);
                    break;

                case "openFolder":
                    var folder = Environment.GetFolderPath(Environment.SpecialFolder.UserProfile);
                    var dlFolder = Path.Combine(folder, "Downloads");
                    if (Directory.Exists(dlFolder))
                    {
                        Process.Start(new ProcessStartInfo("explorer.exe", dlFolder) { UseShellExecute = true });
                    }
                    break;

                case "fetchIranflix":
                    var reqId = root.GetProperty("requestId").GetString();
                    var endpoint = root.GetProperty("endpoint").GetString();
                    _ = FetchIranflixAsync(reqId, endpoint);
                    break;

                case "openUrl":
                    var url = root.GetProperty("url").GetString();
                    if (!string.IsNullOrEmpty(url))
                    {
                        Process.Start(new ProcessStartInfo(url) { UseShellExecute = true });
                    }
                    break;

                case "getAppInfo":
                    _ = PostAppInfoAsync();
                    break;

                case "checkUpdates":
                    _ = CheckForUpdatesAsync();
                    break;
            }
        }
        catch (Exception ex)
        {
            Debug.WriteLine($"[WebMessageError] {ex.Message}");
        }
    }

    private async Task FetchIranflixAsync(string? requestId, string? endpoint)
    {
        if (string.IsNullOrEmpty(requestId) || string.IsNullOrEmpty(endpoint)) return;

        string? resultJson = null;
        Exception? lastEx = null;

        // Ensure endpoint ends with / for Iranflix API
        var normalizedEndpoint = endpoint.EndsWith("/") ? endpoint : endpoint + "/";

        foreach (var server in IranflixServers)
        {
            try
            {
                using var cts = new System.Threading.CancellationTokenSource(TimeSpan.FromSeconds(6));
                var cleanServer = server.TrimEnd('/');
                var fullUrl = $"{cleanServer}{normalizedEndpoint}".Replace("{API_KEY}", ApiKey);
                var response = await _httpClient.GetAsync(fullUrl, cts.Token);
                if (response.IsSuccessStatusCode)
                {
                    resultJson = await response.Content.ReadAsStringAsync(cts.Token);
                    if (!string.IsNullOrWhiteSpace(resultJson) && (resultJson.TrimStart().StartsWith("[") || resultJson.TrimStart().StartsWith("{")))
                    {
                        break;
                    }
                }
            }
            catch (Exception ex)
            {
                lastEx = ex;
                Debug.WriteLine($"[IranflixFetchError] {server} => {ex.Message}");
            }
        }

        try
        {
            object? parsedData = null;
            if (!string.IsNullOrEmpty(resultJson))
            {
                try
                {
                    parsedData = JsonDocument.Parse(resultJson).RootElement;
                }
                catch
                {
                    parsedData = resultJson;
                }
            }

            var responsePayload = new
            {
                action = "iranflixResponse",
                requestId,
                success = resultJson != null,
                data = parsedData,
                error = lastEx?.Message
            };

            var jsonStr = JsonSerializer.Serialize(responsePayload);
            await Dispatcher.InvokeAsync(() =>
            {
                if (webView?.CoreWebView2 != null)
                {
                    webView.CoreWebView2.PostWebMessageAsString(jsonStr);
                }
            });
        }
        catch (Exception ex)
        {
            Debug.WriteLine($"[PostMessageError] {ex.Message}");
        }
    }

    private void LaunchPlayer(string? player, string? url, string? title)
    {
        if (string.IsNullOrEmpty(url)) return;

        // Common player paths on Windows
        string? exePath = null;
        string args = $"\"{url}\"";

        switch (player?.ToLower())
        {
            case "auto":
                exePath = FindExecutable(
                    @"C:\Program Files\VideoLAN\VLC\vlc.exe",
                    @"C:\Program Files\DAUM\PotPlayer\PotPlayer64.exe",
                    @"C:\Program Files\DAUM\PotPlayer\PotPlayerMini64.exe",
                    @"C:\Program Files\MPC-HC\mpc-hc64.exe",
                    @"C:\Program Files\KMPlayer 64X\KMPlayer64.exe",
                    @"C:\Program Files (x86)\VideoLAN\VLC\vlc.exe",
                    @"C:\Program Files (x86)\DAUM\PotPlayer\PotPlayer.exe",
                    @"C:\Program Files (x86)\MPC-HC\mpc-hc.exe"
                );
                break;

            case "vlc":
                exePath = FindExecutable(
                    @"C:\Program Files\VideoLAN\VLC\vlc.exe",
                    @"C:\Program Files (x86)\VideoLAN\VLC\vlc.exe"
                );
                args = $"\"{url}\" --meta-title=\"{title}\"";
                break;

            case "potplayer":
                exePath = FindExecutable(
                    @"C:\Program Files\DAUM\PotPlayer\PotPlayer64.exe",
                    @"C:\Program Files\DAUM\PotPlayer\PotPlayerMini64.exe",
                    @"C:\Program Files (x86)\DAUM\PotPlayer\PotPlayer.exe"
                );
                break;

            case "kmplayer":
                exePath = FindExecutable(
                    @"C:\Program Files\KMPlayer 64X\KMPlayer64.exe",
                    @"C:\Program Files (x86)\The KMPlayer\KMPlayer.exe"
                );
                break;

            case "mpc":
            case "mpc-hc":
                exePath = FindExecutable(
                    @"C:\Program Files\MPC-HC\mpc-hc64.exe",
                    @"C:\Program Files (x86)\MPC-HC\mpc-hc.exe"
                );
                break;
        }

        if (exePath != null && File.Exists(exePath))
        {
            Process.Start(new ProcessStartInfo(exePath, args) { UseShellExecute = true });
        }
        else
        {
            // Fallback: Windows default media handler for video URLs
            try
            {
                Process.Start(new ProcessStartInfo(url) { UseShellExecute = true });
            }
            catch
            {
                MessageBox.Show($"پلیر «{player}» در سیستم یافت نشد. می‌توانید از پلیر پیش‌فرض یا داخلی بیسنور استفاده کنید.", "بیسنور دسکتاپ");
            }
        }
    }

    private void LaunchIDM(string? url)
    {
        if (string.IsNullOrEmpty(url)) return;

        var idmPath = FindExecutable(
            @"C:\Program Files (x86)\Internet Download Manager\IDMan.exe",
            @"C:\Program Files\Internet Download Manager\IDMan.exe"
        );

        if (idmPath != null && File.Exists(idmPath))
        {
            Process.Start(new ProcessStartInfo(idmPath, $"/d \"{url}\"") { UseShellExecute = true });
        }
        else
        {
            Process.Start(new ProcessStartInfo(url) { UseShellExecute = true });
        }
    }

    private static string? FindExecutable(params string[] paths)
    {
        foreach (var p in paths)
        {
            if (File.Exists(p)) return p;
        }
        return null;
    }

    private async Task PostAppInfoAsync()
    {
        try
        {
            var appVersion = typeof(MainWindow).Assembly.GetName().Version?.ToString(3) ?? "5.1.1";
            var payload = new
            {
                action = "appInfoResponse",
                version = appVersion,
                channel = "Experimental",
                runtime = ".NET 10 & WebView2",
                packageId = "HNN.Bisnor"
            };
            var json = JsonSerializer.Serialize(payload);
            await Dispatcher.InvokeAsync(() =>
            {
                webView?.CoreWebView2?.PostWebMessageAsString(json);
            });
        }
        catch (Exception ex)
        {
            Debug.WriteLine($"[PostAppInfoError] {ex.Message}");
        }
    }

    private async Task CheckForUpdatesAsync()
    {
        try
        {
            var currentVersionStr = typeof(MainWindow).Assembly.GetName().Version?.ToString(3) ?? "5.1.1";
            string latestTag = "";
            string releaseUrl = "https://github.com/nikan48g/Bisnor/releases";
            string releaseNotes = "";
            bool hasNewVersion = false;

            try
            {
                using var req = new HttpRequestMessage(HttpMethod.Get, "https://api.github.com/repos/nikan48g/Bisnor/releases/latest");
                req.Headers.UserAgent.ParseAdd("BisnorDesktop-UpdateChecker");
                var resp = await _httpClient.SendAsync(req);
                if (resp.IsSuccessStatusCode)
                {
                    var jsonStr = await resp.Content.ReadAsStringAsync();
                    using var doc = JsonDocument.Parse(jsonStr);
                    latestTag = doc.RootElement.TryGetProperty("tag_name", out var t) ? t.GetString() ?? "" : "";
                    releaseUrl = doc.RootElement.TryGetProperty("html_url", out var u) ? u.GetString() ?? releaseUrl : releaseUrl;
                    releaseNotes = doc.RootElement.TryGetProperty("body", out var b) ? b.GetString() ?? "" : "";

                    var cleanRemote = latestTag.TrimStart('v', 'V').Split('-')[0];
                    hasNewVersion = IsNewerVersion(cleanRemote, currentVersionStr);
                }
            }
            catch (Exception fetchEx)
            {
                Debug.WriteLine($"[UpdateFetchError] {fetchEx.Message}");
            }

            var payload = new
            {
                action = "updateCheckResult",
                currentVersion = currentVersionStr,
                latestVersion = string.IsNullOrEmpty(latestTag) ? currentVersionStr : latestTag,
                hasNewVersion,
                releaseUrl,
                notes = releaseNotes,
                wingetCommand = "winget upgrade HNN.Bisnor"
            };

            var json = JsonSerializer.Serialize(payload);
            await Dispatcher.InvokeAsync(() =>
            {
                webView?.CoreWebView2?.PostWebMessageAsString(json);
            });
        }
        catch (Exception ex)
        {
            Debug.WriteLine($"[CheckUpdatesError] {ex.Message}");
        }
    }

    private static bool IsNewerVersion(string remote, string local)
    {
        var rParts = remote.Split('.').Select(p => int.TryParse(p, out var v) ? v : 0).ToList();
        var lParts = local.Split('.').Select(p => int.TryParse(p, out var v) ? v : 0).ToList();
        for (int i = 0; i < Math.Max(rParts.Count, lParts.Count); i++)
        {
            int r = i < rParts.Count ? rParts[i] : 0;
            int l = i < lParts.Count ? lParts[i] : 0;
            if (r > l) return true;
            if (r < l) return false;
        }
        return false;
    }
}

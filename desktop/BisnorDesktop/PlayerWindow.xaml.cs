using System;
using System.Diagnostics;
using System.Windows;
using System.Windows.Input;
using System.Windows.Threading;

namespace BisnorDesktop;

public partial class PlayerWindow : Window
{
    private readonly DispatcherTimer _timer;
    private readonly string _url;
    private bool _isDraggingSeek;
    private bool _controlsVisible;
    private DispatcherTimer? _hideControlsTimer;

    public PlayerWindow(string url, string title)
    {
        InitializeComponent();
        _url = url;

        TitleText.Text = string.IsNullOrWhiteSpace(title) ? "پخش بیسنور" : title;
        VolumeBar.Value = 0.9;
        Media.Volume = 0.9;

        _timer = new DispatcherTimer { Interval = TimeSpan.FromMilliseconds(500) };
        _timer.Tick += (_, _) => UpdateProgress();

        Loaded += (_, _) =>
        {
            ShowControls();
            StartPlayback();
        };
        Closed += (_, _) =>
        {
            _timer.Stop();
            _hideControlsTimer?.Stop();
            try { Media.Stop(); Media.Close(); } catch { }
        };

        MouseMove += (_, _) => ShowControls();
        MouseLeftButtonDown += (_, _) => ShowControls();
        KeyDown += PlayerWindow_KeyDown;
    }

    private void StartPlayback()
    {
        try
        {
            Media.Source = new Uri(_url);
            Media.Play();
            _timer.Start();
        }
        catch (Exception ex)
        {
            ShowError(ex.Message);
        }
    }

    private void Media_MediaOpened(object sender, RoutedEventArgs e)
    {
        PlayPauseButton.Content = "❚❚";
        VolumeBar.Value = Media.Volume;
    }

    private void Media_MediaFailed(object sender, ExceptionRoutedEventArgs e)
    {
        ShowError(e.ErrorException?.Message ?? "کدک یا کانتینر توسط Media Foundation پشتیبانی نمی‌شود.");
    }

    private void Media_MediaEnded(object sender, RoutedEventArgs e)
    {
        SeekBar.Value = 0;
        PlayPauseButton.Content = "▶";
        Media.Position = TimeSpan.Zero;
        Media.Pause();
    }

    private void ShowError(string message)
    {
        _timer.Stop();
        ErrorText.Text = message;
        ErrorOverlay.Visibility = Visibility.Visible;
    }

    private void UpdateProgress()
    {
        if (_isDraggingSeek || !Media.NaturalDuration.HasTimeSpan) return;
        var duration = Media.NaturalDuration.TimeSpan;
        var position = Media.Position;
        if (duration.TotalMilliseconds > 0)
        {
            SeekBar.Maximum = duration.TotalSeconds;
            SeekBar.Value = Math.Clamp(position.TotalSeconds, 0, duration.TotalSeconds);
        }
        TimeText.Text = $"{FormatTime(position)} / {FormatTime(duration)}";
    }

    private static string FormatTime(TimeSpan value)
    {
        return value.TotalHours >= 1
            ? $"{(int)value.TotalHours:00}:{value.Minutes:00}:{value.Seconds:00}"
            : $"{value.Minutes:00}:{value.Seconds:00}";
    }

    private void ShowControls()
    {
        _controlsVisible = true;
        TitleBar.Visibility = Visibility.Visible;
        ControlBar.Visibility = Visibility.Visible;
        Cursor = Cursors.Arrow;

        _hideControlsTimer?.Stop();
        _hideControlsTimer = new DispatcherTimer { Interval = TimeSpan.FromSeconds(3) };
        _hideControlsTimer.Tick += (_, _) =>
        {
            _hideControlsTimer?.Stop();
            if (Media.IsLoaded && !_isDraggingSeek)
            {
                TitleBar.Visibility = Visibility.Collapsed;
                ControlBar.Visibility = Visibility.Collapsed;
                Cursor = Cursors.None;
                _controlsVisible = false;
            }
        };
        _hideControlsTimer.Start();
    }

    private void PlayerWindow_KeyDown(object sender, KeyEventArgs e)
    {
        switch (e.Key)
        {
            case Key.Escape:
                Close();
                break;
            case Key.Space:
                TogglePlayPause();
                e.Handled = true;
                break;
            case Key.Left:
                Media.Position = Media.Position - TimeSpan.FromSeconds(10);
                ShowControls();
                e.Handled = true;
                break;
            case Key.Right:
                Media.Position = Media.Position + TimeSpan.FromSeconds(10);
                ShowControls();
                e.Handled = true;
                break;
        }
    }

    private void TogglePlayPause()
    {
        if (Media.CanPause)
        {
            Media.Pause();
            PlayPauseButton.Content = "▶";
        }
        else
        {
            Media.Play();
            PlayPauseButton.Content = "❚❚";
        }
    }

    private void PlayPauseButton_Click(object sender, RoutedEventArgs e)
    {
        TogglePlayPause();
        ShowControls();
    }

    private void RewindButton_Click(object sender, RoutedEventArgs e)
    {
        Media.Position = Media.Position - TimeSpan.FromSeconds(10);
        ShowControls();
    }

    private void ForwardButton_Click(object sender, RoutedEventArgs e)
    {
        Media.Position = Media.Position + TimeSpan.FromSeconds(10);
        ShowControls();
    }

    private void SeekBar_DragStarted(object sender, RoutedEventArgs e)
    {
        _isDraggingSeek = true;
    }

    private void SeekBar_DragCompleted(object sender, RoutedEventArgs e)
    {
        _isDraggingSeek = false;
        Media.Position = TimeSpan.FromSeconds(SeekBar.Value);
        ShowControls();
    }

    private void SeekBar_ValueChanged(object sender, RoutedPropertyChangedEventArgs<double> e)
    {
        if (_isDraggingSeek)
        {
            var preview = TimeSpan.FromSeconds(e.NewValue);
            TimeText.Text = $"{FormatTime(preview)} / {FormatTime(Media.NaturalDuration.HasTimeSpan ? Media.NaturalDuration.TimeSpan : TimeSpan.Zero)}";
        }
    }

    private void VolumeBar_ValueChanged(object sender, RoutedPropertyChangedEventArgs<double> e)
    {
        if (Media != null) Media.Volume = e.NewValue;
        if (MuteButton != null) MuteButton.Content = e.NewValue <= 0.001 ? "🔇" : "🔊";
    }

    private void MuteButton_Click(object sender, RoutedEventArgs e)
    {
        Media.IsMuted = !Media.IsMuted;
        MuteButton.Content = Media.IsMuted ? "🔇" : "🔊";
    }

    private void FullscreenButton_Click(object sender, RoutedEventArgs e)
    {
        WindowState = WindowState == WindowState.Maximized ? WindowState.Normal : WindowState.Maximized;
        ShowControls();
    }

    private void CloseButton_Click(object sender, RoutedEventArgs e)
    {
        Close();
    }

    private void OpenExternal_Click(object sender, RoutedEventArgs e)
    {
        try
        {
            Process.Start(new ProcessStartInfo(_url) { UseShellExecute = true });
        }
        catch (Exception ex)
        {
            MessageBox.Show($"اجرای پلیر خارجی ممکن نشد:\n{ex.Message}", "بیسنور", MessageBoxButton.OK, MessageBoxImage.Warning);
        }
    }
}

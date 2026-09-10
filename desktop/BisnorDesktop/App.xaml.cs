using System.Configuration;
using System.Data;
using System.Windows;

namespace BisnorDesktop;

/// <summary>
/// Interaction logic for App.xaml
/// </summary>
public partial class App : Application
{
    protected override void OnStartup(StartupEventArgs e)
    {
        base.OnStartup(e);

        DispatcherUnhandledException += (s, args) =>
        {
            MessageBox.Show($"خطای غیرمنتظره در اجرای بیسنور:\n{args.Exception.Message}\n\n{args.Exception.StackTrace}", "خطای بیسنور", MessageBoxButton.OK, MessageBoxImage.Error);
            args.Handled = true;
        };

        AppDomain.CurrentDomain.UnhandledException += (s, args) =>
        {
            if (args.ExceptionObject is Exception ex)
            {
                MessageBox.Show($"خطای بحرانی در اجرای بیسنور:\n{ex.Message}\n\n{ex.StackTrace}", "خطای بیسنور", MessageBoxButton.OK, MessageBoxImage.Error);
            }
        };

        try
        {
            var mainWindow = new MainWindow();
            MainWindow = mainWindow;
            mainWindow.Show();
        }
        catch (Exception ex)
        {
            MessageBox.Show($"خطا در شروع پنجره اصلی بیسنور:\n{ex.Message}\n\n{ex.InnerException?.Message}\n\n{ex.StackTrace}", "خطای راه‌اندازی", MessageBoxButton.OK, MessageBoxImage.Error);
        }
    }
}


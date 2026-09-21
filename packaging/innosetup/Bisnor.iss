; =====================================================================
; Bisnor Cinema Desktop - Inno Setup Script
; Publisher: HNN
; Package Identifier: HNN.Bisnor
; Status: Experimental
; =====================================================================

#ifndef MyAppVersion
#define MyAppVersion "5.1.2"
#endif

#define MyAppName "Bisnor"
#define MyAppPublisher "HNN"
#define MyAppURL "https://github.com/nikan48g/Bisnor"
#define MyAppExeName "Bisnor.exe"
#define MyAppId "{{E1D4A738-4F92-4C61-9E2F-068DF14D9842}"

[Setup]
; App Identity
AppId={#MyAppId}
AppName={#MyAppName}
AppVersion={#MyAppVersion}
AppVerName={#MyAppName} v{#MyAppVersion} (Experimental)
AppPublisher={#MyAppPublisher}
AppPublisherURL={#MyAppURL}
AppSupportURL={#MyAppURL}/issues
AppUpdatesURL={#MyAppURL}/releases

; Installation Directory & Permissions
; Inno Setup 6: PrivilegesRequired=lowest installs to {localappdata}\Programs by default (No Admin / UAC prompt needed)
PrivilegesRequired=lowest
PrivilegesRequiredOverridesAllowed=dialog
DefaultDirName={autopf}\{#MyAppName}
DefaultGroupName={#MyAppName}
DisableProgramGroupPage=yes
UsePreviousAppDir=yes

; Architecture (x64)
ArchitecturesAllowed=x64compatible
ArchitecturesInstallIn64BitMode=x64compatible

; Visuals & Branding
SetupIconFile=..\..\desktop\BisnorDesktop\wwwroot\assets\logo.ico
UninstallDisplayIcon={app}\{#MyAppExeName}
UninstallDisplayName={#MyAppName}
ShowLanguageDialog=no

; Output Configuration
OutputDir=Output
OutputBaseFilename=Bisnor-{#MyAppVersion}-win-x64
Compression=lzma2/ultra64
SolidCompression=yes

; Silent / WinGet Support
CloseApplications=force
RestartApplications=no

; Ensure User Settings & Data in %LOCALAPPDATA%\BisnorDesktop are preserved
; App files only exist in {app}

[Languages]
Name: "english"; MessagesFile: "compiler:Default.isl"

[Tasks]
Name: "desktopicon"; Description: "{cm:CreateDesktopIcon}"; GroupDescription: "{cm:AdditionalIcons}"; Flags: unchecked

[Files]
Source: "..\..\desktop\publish\*"; DestDir: "{app}"; Flags: ignoreversion recursesubdirs createallsubdirs

[Icons]
Name: "{autoprograms}\{#MyAppName}"; Filename: "{app}\{#MyAppExeName}"; IconFilename: "{app}\{#MyAppExeName}"
Name: "{autodesktop}\{#MyAppName}"; Filename: "{app}\{#MyAppExeName}"; IconFilename: "{app}\{#MyAppExeName}"; Tasks: desktopicon

[Run]
Filename: "{app}\{#MyAppExeName}"; Description: "{cm:LaunchProgram,{#StringChange(MyAppName, '&', '&&')}}"; Flags: nowait postinstall skipifsilent

[UninstallDelete]
; Clean up any temp or runtime files left in the installation directory only
Type: filesandordirs; Name: "{app}\runtimes"

Set fso = CreateObject("Scripting.FileSystemObject")
base = fso.GetParentFolderName(WScript.ScriptFullName)
appDir = base & "\app"
pending = appDir & "\pending"
logPath = appDir & "\update.log"

' Best-effort diagnostics: every launch and update attempt is recorded here so
' a failed update can be diagnosed from the log instead of a vanished window.
Sub Log(msg)
  On Error Resume Next
  Dim ts
  Set ts = fso.OpenTextFile(logPath, 8, True)
  ts.WriteLine Now & " [launcher] " & msg
  ts.Close
  On Error GoTo 0
End Sub

' Apply a staged in-app update before launching. The game cannot overwrite its
' own jars while running (Windows holds them open), so the updater stages new
' jars into app\pending and the launcher swaps them in here, pre-launch.
'
' IMPORTANT: the updater relaunches this script the instant it spawns it, while
' the old game JVM is still shutting down and still holding app\*.jar open.
' Windows refuses to delete/rename an open file, so the swap below RETRIES
' until the locks are released (up to 60s). Without the retry the script died
' here with "Permission denied", aborted before launching the game, and left
' a half-applied update behind.
If fso.FolderExists(pending) Then
  Log "Staged update found in " & pending & "; applying."
  Dim maxTries, tries, blocked, f, dest
  maxTries = 120 ' 500ms x 120 = 60s for the old instance to release jar locks
  tries = 0
  blocked = ""
  Do
    blocked = ""
    On Error Resume Next
    For Each f In fso.GetFolder(pending).Files
      dest = appDir & "\" & f.Name
      If fso.FileExists(dest) Then
        fso.DeleteFile dest, True
        If Err.Number <> 0 Then
          blocked = f.Name & " (delete: " & Err.Description & ")"
          Err.Clear
          Exit For
        End If
      End If
      fso.MoveFile f.Path, dest
      If Err.Number <> 0 Then
        blocked = f.Name & " (move: " & Err.Description & ")"
        Err.Clear
        Exit For
      End If
    Next
    On Error GoTo 0
    If blocked = "" Then Exit Do
    tries = tries + 1
    If tries = 1 Or tries Mod 20 = 0 Then _
      Log "Still waiting for jar locks (" & blocked & "), try " & tries & "/" & maxTries
    If tries >= maxTries Then Exit Do
    WScript.Sleep 500
  Loop
  If blocked = "" Then
    On Error Resume Next
    fso.DeleteFolder pending, True
    On Error GoTo 0
    Log "Staged update applied successfully."
  Else
    Log "ERROR: could not apply staged update after " & maxTries & _
        " tries; still blocked on " & blocked & ". NOT launching."
    MsgBox "Infinite Conquest could not apply the downloaded update." & vbCrLf & vbCrLf & _
           "Blocked by: " & blocked & vbCrLf & vbCrLf & _
           "Make sure no other Infinite Conquest window is running, then launch " & _
           "the game again - the update will be retried." & vbCrLf & vbCrLf & _
           "Details were written to app\update.log next to the launcher.", _
           vbExclamation, "Infinite Conquest Update"
    WScript.Quit 1
  End If
End If

Dim sh, cmd
Set sh = CreateObject("WScript.Shell")
cmd = """" & base & "\jre\bin\javaw.exe" & """ -jar """ & base & "\app\infinite-conquest-gui.jar"""
Log "Launching: " & cmd
sh.Run cmd, 0, False

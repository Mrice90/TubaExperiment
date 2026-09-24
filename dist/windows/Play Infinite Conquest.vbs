Set fso = CreateObject("Scripting.FileSystemObject")
base = fso.GetParentFolderName(WScript.ScriptFullName)

' Apply a staged in-app update before launching. The game cannot overwrite its
' own jars while running (Windows holds them open), so the updater stages new
' jars into app\pending and the launcher swaps them in here, pre-launch.
pending = base & "\app\pending"
If fso.FolderExists(pending) Then
  For Each f In fso.GetFolder(pending).Files
    dest = base & "\app\" & f.Name
    If fso.FileExists(dest) Then fso.DeleteFile dest, True
    fso.MoveFile f.Path, dest
  Next
  fso.DeleteFolder pending, True
End If

Set sh = CreateObject("WScript.Shell")
sh.Run """" & base & "\jre\bin\javaw.exe" & """ -jar """ & base & "\app\infinite-conquest-gui.jar""", 0, False

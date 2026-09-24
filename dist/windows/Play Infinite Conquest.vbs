Set fso = CreateObject("Scripting.FileSystemObject")
base = fso.GetParentFolderName(WScript.ScriptFullName)
Set sh = CreateObject("WScript.Shell")
sh.Run """" & base & "\jre\bin\javaw.exe" & """ -jar """ & base & "\app\infinite-conquest-gui.jar""", 0, False

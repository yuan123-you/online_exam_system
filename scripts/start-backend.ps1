if ([string]::IsNullOrWhiteSpace($env:AI_API_KEY)) { throw "AI_API_KEY must be provided through the environment." }
$cp = Get-Content -Raw 'backend/target/cp.txt'
$cp = $cp.Trim()
$fullCp = "backend/target/classes;$cp"
Start-Process -FilePath 'java' -ArgumentList "-Dfile.encoding=UTF-8", "-Dsun.jnu.encoding=UTF-8", "-cp", $fullCp, "com.onlineexam.OnlineExamApplication" -NoNewWindow

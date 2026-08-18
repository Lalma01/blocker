# ── PS-BLOCK installer/uninstaller password prompt ──────────────────────────
# Shown by installer.nsh (via System::Call + nsExec, no plugin needed) before
# the NSIS installer/uninstaller is allowed to tear down tamper protection.
# Ships as a plain app asset (not embedded in the NSIS script) so it travels
# with every install and is available to both the installer and the
# uninstaller without any NSIS-side file-embedding tricks.
#
# On OK: writes the entered password (UTF-8, no BOM, no trailing newline) to
# -OutFile and exits 0. On Cancel/close: writes nothing and exits 1.
param(
  [Parameter(Mandatory = $true)]
  [string]$OutFile
)

Add-Type -AssemblyName System.Windows.Forms
Add-Type -AssemblyName System.Drawing

$hu = (Get-Culture).TwoLetterISOLanguageName -eq 'hu'
$titleText  = if ($hu) { 'PS-BLOCK — jelszó szükséges' }            else { 'PS-BLOCK — password required' }
$labelText  = if ($hu) { 'Add meg a jelszót a folytatáshoz:' }      else { 'Enter the password to continue:' }
$okText     = if ($hu) { 'OK' }                                     else { 'OK' }
$cancelText = if ($hu) { 'Mégse' }                                  else { 'Cancel' }

$form = New-Object System.Windows.Forms.Form
$form.Text = $titleText
$form.ClientSize = New-Object System.Drawing.Size(340, 120)
$form.StartPosition = 'CenterScreen'
$form.FormBorderStyle = 'FixedDialog'
$form.MaximizeBox = $false
$form.MinimizeBox = $false
$form.TopMost = $true

$label = New-Object System.Windows.Forms.Label
$label.Text = $labelText
$label.SetBounds(16, 14, 308, 20)
$form.Controls.Add($label)

$box = New-Object System.Windows.Forms.TextBox
$box.SetBounds(16, 40, 308, 24)
$box.UseSystemPasswordChar = $true
$form.Controls.Add($box)

$okBtn = New-Object System.Windows.Forms.Button
$okBtn.Text = $okText
$okBtn.SetBounds(148, 76, 84, 28)
$okBtn.DialogResult = [System.Windows.Forms.DialogResult]::OK
$form.Controls.Add($okBtn)

$cancelBtn = New-Object System.Windows.Forms.Button
$cancelBtn.Text = $cancelText
$cancelBtn.SetBounds(240, 76, 84, 28)
$cancelBtn.DialogResult = [System.Windows.Forms.DialogResult]::Cancel
$form.Controls.Add($cancelBtn)

$form.AcceptButton = $okBtn
$form.CancelButton = $cancelBtn
$form.Add_Shown({ $form.Activate() | Out-Null; $box.Focus() | Out-Null })

$result = $form.ShowDialog()

if ($result -eq [System.Windows.Forms.DialogResult]::OK -and $box.Text.Length -gt 0) {
  $utf8NoBom = New-Object System.Text.UTF8Encoding($false)
  [System.IO.File]::WriteAllText($OutFile, $box.Text, $utf8NoBom)
  exit 0
} else {
  exit 1
}

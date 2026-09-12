在 VS Code 中可以使用内置终端执行 `cmd` 命令。

打开终端：

```text
Ctrl + `
```

选择终端右上角的下拉菜单，选择 **Command Prompt**。

**新建文件夹**

```cmd
mkdir frontend
mkdir backend docs
```

嵌套创建：

```cmd
mkdir src\components
```

进入文件夹：

```cmd
cd frontend
```

返回上一级：

```cmd
cd ..
```

返回项目根目录：

```cmd
cd /d ~\projects\docker-handson\devsecops-task-app
```

**新建文件**

```cmd
type nul > README.md
type nul > .gitignore
```

在指定目录创建：

```cmd
type nul > backend\Dockerfile
```

创建并写入内容：

```cmd
echo # DevSecOps Task App > README.md
```

注意：`>` 会覆盖原文件，追加内容使用：

```cmd
echo More content >> README.md
```

**查看目录**

```cmd
dir
```

查看隐藏文件：

```cmd
dir /a
```

**删除文件或文件夹**

```cmd
del test.txt
rmdir empty-folder
rmdir /s /q old-folder
```

`/s /q` 会递归且不询问地删除，请谨慎使用。

**用 VS Code 打开文件**

```cmd
code README.md
code .
```

**当前项目示例**

```cmd
cd /d ~\projects\docker-handson\devsecops-task-app

mkdir frontend backend docs

type nul > README.md
type nul > docker-compose.yml
type nul > backend\Dockerfile

code .
```

PowerShell 中对应命令通常是：

```powershell
New-Item -ItemType Directory frontend
New-Item README.md -ItemType File
```

所以如果终端提示符是 `PS C:\...>`，使用的是 PowerShell；如果显示普通路径提示符，则可能是 `cmd`。
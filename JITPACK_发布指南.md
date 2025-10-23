# JitPack 发布指南

本项目包含两个独立的library模块，可以分别发布到JitPack供其他项目使用。

## 📦 可用的Library

1. **AntiCheat** - 游戏反作弊库
2. **Savelibrary** - 游戏存档管理库

---

## 🚀 发布步骤

### 1. 提交代码到GitHub

确保您的代码已经推送到GitHub仓库：

```bash
git add .
git commit -m "准备发布到JitPack"
git push origin master
```

### 2. 创建Release标签

在GitHub上创建一个Release：

**方式一：通过GitHub网页**
1. 进入您的GitHub仓库
2. 点击 "Releases" → "Create a new release"
3. 填写标签版本号（例如：`v1.0.0`）
4. 填写Release标题和说明
5. 点击 "Publish release"

**方式二：通过命令行**
```bash
git tag -a v1.0.0 -m "首次发布"
git push origin v1.0.0
```

### 3. 在JitPack上构建

1. 访问 https://jitpack.io
2. 输入您的仓库地址：`com.github.YourUsername/YourRepo`
3. 选择刚才创建的版本标签（例如：`v1.0.0`）
4. 点击 "Get it" 触发构建
5. 等待构建完成（绿色对勾表示成功）

---

## 📥 如何在其他项目中使用

### 步骤1：添加JitPack仓库

在项目的 `settings.gradle` 文件中添加JitPack仓库：

```groovy
dependencyResolutionManagement {
    repositoriesMode.set(RepositoriesMode.FAIL_ON_PROJECT_REPOS)
    repositories {
        google()
        mavenCentral()
        maven { url 'https://jitpack.io' }  // 添加这一行
    }
}
```

### 步骤2：添加依赖

在应用模块的 `build.gradle` 中添加所需的library依赖：

#### 使用 AntiCheat 库

```groovy
dependencies {
    implementation 'com.github.YourUsername.YourRepo:AntiCheat:v1.0.0'
}
```

#### 使用 Savelibrary 库

```groovy
dependencies {
    implementation 'com.github.YourUsername.YourRepo:Savelibrary:v1.0.0'
}
```

#### 同时使用两个库

```groovy
dependencies {
    implementation 'com.github.YourUsername.YourRepo:AntiCheat:v1.0.0'
    implementation 'com.github.YourUsername.YourRepo:Savelibrary:v1.0.0'
}
```

---

## 🔖 版本说明

### 使用特定版本

```groovy
implementation 'com.github.YourUsername.YourRepo:AntiCheat:v1.0.0'
```

### 使用最新版本（不推荐生产环境）

```groovy
implementation 'com.github.YourUsername.YourRepo:AntiCheat:master-SNAPSHOT'
```

### 使用特定commit

```groovy
implementation 'com.github.YourUsername.YourRepo:AntiCheat:commit-hash'
```

---

## 📝 注意事项

### 1. 修改groupId

在发布前，请修改两个library的 `build.gradle` 文件中的 `groupId`：

**AntiCheat/build.gradle:**
```groovy
afterEvaluate {
    publishing {
        publications {
            release(MavenPublication) {
                from components.release
                groupId = 'com.github.YourGitHubUsername'  // 改为您的GitHub用户名
                artifactId = 'anticheat'
                version = '1.0.0'
            }
        }
    }
}
```

**Savelibrary/build.gradle:**
```groovy
afterEvaluate {
    publishing {
        publications {
            release(MavenPublication) {
                from components.release
                groupId = 'com.github.YourGitHubUsername'  // 改为您的GitHub用户名
                artifactId = 'savelibrary'
                version = '1.0.0'
            }
        }
    }
}
```

### 2. JitPack构建要求

- ✅ 代码必须在GitHub上（也支持GitLab、Bitbucket）
- ✅ 必须有Git标签或Release
- ✅ 项目必须能够成功编译
- ✅ 确保没有编译错误

### 3. 常见问题

**Q: JitPack构建失败怎么办？**
- 点击JitPack上的 "Log" 查看构建日志
- 检查项目是否能在本地成功编译
- 确保所有依赖都可以正常下载

**Q: 如何更新已发布的版本？**
- 创建新的Git标签（例如：v1.0.1）
- 在JitPack上触发新版本的构建

**Q: 可以删除已发布的版本吗？**
- JitPack的构建是不可变的，无法删除
- 建议使用新版本号覆盖

---

## 🔗 引用格式

完整的依赖引用格式为：

```
com.github.{GitHub用户名}.{仓库名}:{模块名}:{版本号}
```

例如：
```groovy
implementation 'com.github.john.GameUtil:AntiCheat:v1.0.0'
implementation 'com.github.john.GameUtil:Savelibrary:v1.0.0'
```

---

## 📊 添加徽章到README

您可以在README.md中添加JitPack徽章来显示最新版本：

```markdown
[![](https://jitpack.io/v/YourUsername/YourRepo.svg)](https://jitpack.io/#YourUsername/YourRepo)
```

---

## 🆘 获取帮助

- JitPack官方文档: https://jitpack.io/docs/
- 私有仓库发布: https://jitpack.io/private
- 问题反馈: https://github.com/jitpack/jitpack.io/issues


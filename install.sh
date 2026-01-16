#!/bin/bash

# 配置参数
SDK_ROOT="$HOME/android-sdk"
JDK_PATH="$SDK_ROOT/jdk-17"

echo "🚀 开始安装 Android SDK 环境..."

# 1. 创建目录
mkdir -p "$SDK_ROOT"
cd "$SDK_ROOT"

# 2. 安装 OpenJDK 17 (以源码/归档版方式安装以保证独立性)
echo "正在下载 OpenJDK 17..."
wget -qO jdk17.tar.gz 'https://aka.ms/download-jdk/microsoft-jdk-17.0.10-linux-x64.tar.gz'
mkdir -p "$JDK_PATH"
tar -xzf jdk17.tar.gz -C "$JDK_PATH" --strip-components=1
rm jdk17.tar.gz


# 配置项：可以根据需要修改版本号
SDK_VERSION="11076708" # 对应 Command Line Tools 的版本号
ANDROID_HOME="$HOME/android-sdk"
PATH_CONFIG="$HOME/.bashrc"

echo "开始安装 Android SDK..."

# 1. 创建目录
mkdir -p $ANDROID_HOME/cmdline-tools

# 2. 下载 Command Line Tools (从官方获取)
cd /tmp
wget https://dl.google.com/android/repository/commandlinetools-linux-${SDK_VERSION}_latest.zip -O sdk.zip

# 3. 解压
unzip sdk.zip
# 注意：Android SDK 要求的目录结构比较特殊，需要移动到 latest 目录下
mkdir -p $ANDROID_HOME/cmdline-tools/latest
mv cmdline-tools/* $ANDROID_HOME/cmdline-tools/latest/
rm -rf cmdline-tools sdk.zip

# 4. 配置环境变量 (写入 .bashrc)
echo "配置环境变量..."
{
    echo ""
    echo "# Android SDK 路径"
    echo "export JAVA_HOME=$JDK_PATH"
    echo "export ANDROID_HOME=$SDK_ROOT"
    echo 'export PATH=$JAVA_HOME/bin:$PATH'
    echo 'export PATH=$PATH:$ANDROID_HOME/cmdline-tools/latest/bin'
    echo 'export PATH=$PATH:$ANDROID_HOME/platform-tools'
    echo 'export PATH=$PATH:$ANDROID_HOME/build-tools/34.0.0'
} >> "$HOME/.bashrc"

source $PATH_CONFIG

# 5. 接受所有许可协议（这是自动化的关键）
echo "接受许可协议..."
yes | $ANDROID_HOME/cmdline-tools/latest/bin/sdkmanager --sdk_root=$ANDROID_HOME --licenses

# 6. 安装必要的组件
echo "安装 Platform-tools, Build-tools 和 Platforms..."
$ANDROID_HOME/cmdline-tools/latest/bin/sdkmanager --sdk_root=$ANDROID_HOME \
    "platform-tools" \
    "platforms;android-34" \
    "build-tools;34.0.0"

echo "安装完成！请运行 'source ~/.bashrc' 使配置生效。"

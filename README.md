# 五子棋游戏 (Five In Row)

一个基于Android平台的五子棋游戏应用，支持人机对战、游戏存档和历史记录功能。

## 功能特性

- 经典五子棋游戏规则，先连成五子者获胜
- 棋盘大小可配置（默认13x13）
- 落子确认机制，防止误操作
- 游戏进度保存与加载
- 支持查看历史对战记录
- 响应式界面设计，适配不同屏幕尺寸

## 安装说明

### 前提条件
- Android Studio 4.0+ 
- Android SDK 21+ (Android 5.0+)
- Gradle 7.0+

### 构建步骤
1. 克隆本仓库到本地
```bash
https://github.com/yourusername/five-in-row.git
```
2. 使用Android Studio打开项目
3. 等待Gradle同步完成
4. 连接Android设备或启动模拟器
5. 点击"Run"按钮构建并安装应用

## 使用方法

1. 启动应用后自动开始新游戏
2. 点击棋盘交叉点放置棋子
3. 黑方先行，双方交替落子
4. 率先在横、竖或斜方向连成五子者获胜
5. 通过菜单可以保存/加载游戏进度
6. 游戏结束后可选择重新开始

## 技术实现

- 自定义View实现棋盘绘制和交互逻辑
- 使用ByteArray进行游戏数据序列化
- Base64编码处理存档数据
- Gson进行JSON数据解析
- 面向接口设计，实现游戏逻辑与UI分离

## 项目结构

```
app/src/main/java/com/foxluo/fiveinrow/
├── FiveInRowGameView.kt  # 游戏主视图，负责棋盘绘制和交互
├── BoardCache.kt         # 游戏存档管理
├── BoardCacheManager.kt  # 存档持久化处理
└── BoardSerializer.kt    # 游戏数据序列化工具
```

## 许可证

本项目采用MIT许可证 - 详情参见LICENSE文件

## 致谢

- 感谢Android开发社区提供的丰富学习资源
- 感谢所有为本项目贡献代码的开发者
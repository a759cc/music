import 'dart:math' as math;
import 'package:audio_service/audio_service.dart';
import 'package:cached_network_image/cached_network_image.dart';
import 'package:flutter/material.dart';
import 'package:get/get.dart';
import '/ui/player/player_controller.dart';

class DynamicIslandWidget extends StatefulWidget {
  const DynamicIslandWidget({super.key});

  @override
  State<DynamicIslandWidget> createState() => _DynamicIslandWidgetState();
}

class _DynamicIslandWidgetState extends State<DynamicIslandWidget>
    with TickerProviderStateMixin, WidgetsBindingObserver {
  late AnimationController _discRotationController;
  late AnimationController _waveAnimationController;
  final RxBool _isExpanded = false.obs;
  final RxBool _isInBackground = false.obs;

  @override
  void initState() {
    super.initState();
    WidgetsBinding.instance.addObserver(this);
    _discRotationController = AnimationController(
      vsync: this,
      duration: const Duration(seconds: 12),
    );

    _waveAnimationController = AnimationController(
      vsync: this,
      duration: const Duration(milliseconds: 900),
    )..repeat(reverse: true);
  }

  @override
  void dispose() {
    WidgetsBinding.instance.removeObserver(this);
    _discRotationController.dispose();
    _waveAnimationController.dispose();
    super.dispose();
  }

  @override
  void didChangeAppLifecycleState(AppLifecycleState state) {
    if (state == AppLifecycleState.paused ||
        state == AppLifecycleState.hidden ||
        state == AppLifecycleState.inactive) {
      _isInBackground.value = true;
    } else if (state == AppLifecycleState.resumed) {
      _isInBackground.value = false;
      _isExpanded.value = false;
    }
  }

  @override
  Widget build(BuildContext context) {
    final PlayerController playerController = Get.find<PlayerController>();

    return Obx(() {
      final currentSong = playerController.currentSong.value;
      final isInBackground = _isInBackground.value;

      // 仅在后台播放/退到后台时浮现灵动岛
      if (currentSong == null || !isInBackground) {
        _discRotationController.stop();
        return const SizedBox.shrink();
      }

      final isPlaying =
          playerController.buttonState.value == PlayButtonState.playing;

      if (isPlaying) {
        if (!_discRotationController.isAnimating) {
          _discRotationController.repeat();
        }
        if (!_waveAnimationController.isAnimating) {
          _waveAnimationController.repeat(reverse: true);
        }
      } else {
        _discRotationController.stop();
        _waveAnimationController.stop();
      }

      final isExpanded = _isExpanded.value;

      return SafeArea(
        child: Align(
          alignment: Alignment.topCenter,
          child: Padding(
            padding: const EdgeInsets.only(top: 2.0),
            child: AnimatedContainer(
              duration: const Duration(milliseconds: 350),
              curve: Curves.fastOutSlowIn,
              width: isExpanded
                  ? math.min(MediaQuery.of(context).size.width - 24, 340)
                  : 120, // 最小化收起形态：极简精小 120px 胶囊
              height: isExpanded ? 165 : 28, // 高度仅 28px 精准嵌入通知栏/刘海屏
              decoration: BoxDecoration(
                color: Colors.black.withOpacity(0.95),
                borderRadius: BorderRadius.circular(isExpanded ? 28 : 14),
                border: Border.all(
                  color: isPlaying
                      ? Theme.of(context).colorScheme.primary.withOpacity(0.5)
                      : Colors.white24,
                  width: 1.0,
                ),
                boxShadow: [
                  BoxShadow(
                    color: isPlaying
                        ? Theme.of(context).colorScheme.primary.withOpacity(0.4)
                        : Colors.black45,
                    blurRadius: isExpanded ? 20 : 8,
                    spreadRadius: isExpanded ? 1 : 0,
                    offset: const Offset(0, 3),
                  ),
                ],
              ),
              child: ClipRRect(
                borderRadius: BorderRadius.circular(isExpanded ? 28 : 14),
                child: Material(
                  color: Colors.transparent,
                  child: InkWell(
                    onTap: () {
                      _isExpanded.toggle();
                    },
                    child: Padding(
                      padding: EdgeInsets.symmetric(
                        horizontal: isExpanded ? 12.0 : 6.0,
                        vertical: isExpanded ? 8.0 : 2.0,
                      ),
                      child: isExpanded
                          ? _buildExpandedIsland(context, playerController, currentSong, isPlaying)
                          : _buildCollapsedMiniIsland(context, playerController, currentSong, isPlaying),
                    ),
                  ),
                ),
              ),
            ),
          ),
        ),
      );
    });
  }

  /// 展开形态 UI (Expanded Island with Progress Slider & Control Buttons)
  Widget _buildExpandedIsland(BuildContext context,
      PlayerController playerController, MediaItem song, bool isPlaying) {
    return Column(
      mainAxisAlignment: MainAxisAlignment.spaceBetween,
      children: [
        // 顶部歌名与图像
        Row(
          children: [
            RotationTransition(
              turns: _discRotationController,
              child: Container(
                width: 44,
                height: 44,
                decoration: const BoxDecoration(
                  shape: BoxShape.circle,
                  boxShadow: [
                    BoxShadow(
                      color: Colors.black45,
                      blurRadius: 4,
                    ),
                  ],
                ),
                child: ClipOval(
                  child: song.artUri != null
                      ? CachedNetworkImage(
                          imageUrl: song.artUri.toString(),
                          fit: BoxFit.cover,
                          errorWidget: (context, url, error) => Container(
                            color: Colors.grey[900],
                            child: const Icon(Icons.music_note, color: Colors.white70),
                          ),
                        )
                      : Container(
                          color: Colors.grey[900],
                          child: const Icon(Icons.music_note, color: Colors.white70),
                        ),
                ),
              ),
            ),
            const SizedBox(width: 10),
            Expanded(
              child: Column(
                crossAxisAlignment: CrossAxisAlignment.start,
                mainAxisAlignment: MainAxisAlignment.center,
                children: [
                  Text(
                    song.title,
                    maxLines: 1,
                    overflow: TextOverflow.ellipsis,
                    style: const TextStyle(
                      color: Colors.white,
                      fontSize: 14,
                      fontWeight: FontWeight.bold,
                    ),
                  ),
                  const SizedBox(height: 2),
                  Text(
                    song.artist ?? "Unknown Artist",
                    maxLines: 1,
                    overflow: TextOverflow.ellipsis,
                    style: const TextStyle(
                      color: Colors.white70,
                      fontSize: 11,
                    ),
                  ),
                ],
              ),
            ),
            // 动态流光音浪
            SizedBox(
              width: 32,
              height: 20,
              child: AnimatedBuilder(
                animation: _waveAnimationController,
                builder: (context, child) {
                  return CustomPaint(
                    painter: AudioWaveformPainter(
                      progress: _waveAnimationController.value,
                      color: Theme.of(context).colorScheme.primary,
                      isPlaying: isPlaying,
                    ),
                  );
                },
              ),
            ),
          ],
        ),

        // 中间：音乐播放进度条 (Progress Slider)
        Obx(() {
          final position = playerController.progressBarStatus.value.current;
          final duration = playerController.progressBarStatus.value.total;
          final double maxSec = duration.inSeconds > 0 ? duration.inSeconds.toDouble() : 1.0;
          final double currentSec = math.min<double>(position.inSeconds.toDouble(), maxSec);

          return Column(
            children: [
              SliderTheme(
                data: SliderThemeData(
                  trackHeight: 3.0,
                  thumbShape: const RoundSliderThumbShape(enabledThumbRadius: 6),
                  overlayShape: const RoundSliderOverlayShape(overlayRadius: 10),
                  activeTrackColor: Theme.of(context).colorScheme.primary,
                  inactiveTrackColor: Colors.white24,
                  thumbColor: Colors.white,
                ),
                child: Slider(
                  value: currentSec,
                  max: maxSec,
                  onChanged: (val) {
                    playerController.seek(Duration(seconds: val.toInt()));
                  },
                ),
              ),
              Padding(
                padding: const EdgeInsets.symmetric(horizontal: 8.0),
                child: Row(
                  mainAxisAlignment: MainAxisAlignment.spaceBetween,
                  children: [
                    Text(
                      _formatDuration(position),
                      style: const TextStyle(color: Colors.white54, fontSize: 10),
                    ),
                    Text(
                      _formatDuration(duration),
                      style: const TextStyle(color: Colors.white54, fontSize: 10),
                    ),
                  ],
                ),
              ),
            ],
          );
        }),

        // 底部：功能按键
        Row(
          mainAxisAlignment: MainAxisAlignment.spaceEvenly,
          children: [
            IconButton(
              padding: EdgeInsets.zero,
              constraints: const BoxConstraints(),
              icon: const Icon(Icons.skip_previous_rounded,
                  color: Colors.white, size: 26),
              onPressed: playerController.prev,
            ),
            IconButton(
              padding: EdgeInsets.zero,
              constraints: const BoxConstraints(),
              iconSize: 34,
              icon: Icon(
                isPlaying
                    ? Icons.pause_circle_filled_rounded
                    : Icons.play_circle_fill_rounded,
                color: Theme.of(context).colorScheme.primary,
              ),
              onPressed: playerController.playPause,
            ),
            IconButton(
              padding: EdgeInsets.zero,
              constraints: const BoxConstraints(),
              icon: const Icon(Icons.skip_next_rounded,
                  color: Colors.white, size: 26),
              onPressed: playerController.next,
            ),
            IconButton(
              padding: EdgeInsets.zero,
              constraints: const BoxConstraints(),
              icon: const Icon(Icons.keyboard_arrow_up_rounded,
                  color: Colors.white70, size: 22),
              onPressed: () {
                _isExpanded.value = false;
              },
            ),
          ],
        ),
      ],
    );
  }

  /// 最小化收起形态 UI (Ultra-Mini Collapsed Capsule: Width 120px, Height 28px)
  Widget _buildCollapsedMiniIsland(BuildContext context,
      PlayerController playerController, MediaItem song, bool isPlaying) {
    return Row(
      mainAxisAlignment: MainAxisAlignment.spaceBetween,
      crossAxisAlignment: CrossAxisAlignment.center,
      children: [
        // 极简 18px 旋转黑胶唱片
        RotationTransition(
          turns: _discRotationController,
          child: Container(
            width: 18,
            height: 18,
            decoration: const BoxDecoration(
              shape: BoxShape.circle,
            ),
            child: ClipOval(
              child: song.artUri != null
                  ? CachedNetworkImage(
                      imageUrl: song.artUri.toString(),
                      fit: BoxFit.cover,
                      errorWidget: (context, url, error) => Container(
                        color: Colors.grey[850],
                        child: const Icon(Icons.music_note,
                            size: 12, color: Colors.white70),
                      ),
                    )
                  : Container(
                      color: Colors.grey[850],
                      child: const Icon(Icons.music_note,
                          size: 12, color: Colors.white70),
                    ),
            ),
          ),
        ),
        // 居中极简跳动音频律动条
        SizedBox(
          width: 22,
          height: 14,
          child: AnimatedBuilder(
            animation: _waveAnimationController,
            builder: (context, child) {
              return CustomPaint(
                painter: AudioWaveformPainter(
                  progress: _waveAnimationController.value,
                  color: Theme.of(context).colorScheme.primary,
                  isPlaying: isPlaying,
                ),
              );
            },
          ),
        ),
      ],
    );
  }

  String _formatDuration(Duration duration) {
    String twoDigits(int n) => n.toString().padLeft(2, "0");
    final minutes = twoDigits(duration.inMinutes.remainder(60));
    final seconds = twoDigits(duration.inSeconds.remainder(60));
    return "$minutes:$seconds";
  }
}

/// 绘制高质感脉冲流光音浪 (Audio Waveform Painter)
class AudioWaveformPainter extends CustomPainter {
  final double progress;
  final Color color;
  final bool isPlaying;

  AudioWaveformPainter({
    required this.progress,
    required this.color,
    required this.isPlaying,
  });

  @override
  void paint(Canvas canvas, Size size) {
    final paint = Paint()
      ..style = PaintingStyle.fill
      ..strokeCap = StrokeCap.round;

    final barWidth = 2.5;
    final space = 2.0;
    final count = 4;
    final totalWidth = (barWidth * count) + (space * (count - 1));
    final startX = (size.width - totalWidth) / 2;

    final List<double> heights = isPlaying
        ? [
            0.3 + 0.7 * math.sin(progress * math.pi),
            0.8 - 0.6 * math.cos(progress * math.pi * 1.5).abs(),
            0.4 + 0.6 * math.sin(progress * math.pi * 2.0).abs(),
            0.2 + 0.8 * math.cos(progress * math.pi),
          ]
        : [0.25, 0.25, 0.25, 0.25];

    final colors = [
      color,
      color.withOpacity(0.85),
      Colors.cyanAccent,
      Colors.pinkAccent,
    ];

    for (int i = 0; i < count; i++) {
      final x = startX + i * (barWidth + space);
      final currentHeight = math.max(3.0, size.height * heights[i]);
      final y = (size.height - currentHeight) / 2;

      paint.color = colors[i % colors.length];

      final rect = RRect.fromRectAndRadius(
        Rect.fromLTWH(x, y, barWidth, currentHeight),
        const Radius.circular(2),
      );

      canvas.drawRRect(rect, paint);
    }
  }

  @override
  bool shouldRepaint(covariant AudioWaveformPainter oldDelegate) {
    return oldDelegate.progress != progress ||
        oldDelegate.isPlaying != isPlaying ||
        oldDelegate.color != color;
  }
}

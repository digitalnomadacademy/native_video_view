import 'package:flutter/material.dart';
import 'package:native_video_view/native_video_view.dart';

void main() => runApp(MaterialApp(home: MyApp()));

class MyApp extends StatefulWidget {
  @override
  _MyAppState createState() => _MyAppState();
}

class _MyAppState extends State<MyApp> {
  @override
  Widget build(BuildContext context) {
    return Scaffold(
      appBar: AppBar(
        leading: IconButton(
          icon: Icon(Icons.picture_in_picture),
          onPressed: () async {
            if (_controller != null) {
              await _controller.enterPip();
            }
          },
        ),
        title: const Text('Plugin example app'),
      ),
      body: _buildVideoPlayerWidget(),
    );
  }

  VideoViewController _controller;

  Widget _buildVideoPlayerWidget() {
    return Container(
      alignment: Alignment.center,
      child: NativeVideoView(
        keepAspectRatio: true,
        showMediaController: false,
        enableVolumeControl: true,
        useExoPlayer: true,
        onCreated: (controller) async {
          _controller = controller;

          await controller.setVideoSource(
            "https://commondatastorage.googleapis.com/gtv-videos-bucket/sample/BigBuckBunny.mp4",
            sourceType: VideoSourceType.network,
            requestAudioFocus: true,
          );
          controller.play();
        },
        onPrepared: (controller, info) {
          controller.play();
        },
        onError: (controller, what, extra, message) {
          print('Player Error ($what | $extra | $message)');
        },
        onCompletion: (controller) {
          print('Video completed');
        },
      ),
    );
  }
}

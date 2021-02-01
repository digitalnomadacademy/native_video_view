import 'package:flutter/material.dart';
import 'package:native_video_view/native_video_view.dart';

void main() => runApp(MaterialApp(home: MyApp()));

class MyApp extends StatefulWidget {
  @override
  _MyAppState createState() => _MyAppState();
}

class _MyAppState extends State<MyApp> with WidgetsBindingObserver {
  bool inPip = false;
  @override
  void initState() {
    WidgetsBinding.instance.addObserver(this);
    super.initState();
  }

  @override
  void didChangeAppLifecycleState(AppLifecycleState state) {
    if (state == AppLifecycleState.resumed) {
      setState(() {
        inPip = false;
        _controller.play();
      });
    }
    if (state == AppLifecycleState.inactive) {
         inPip = true;
         _controller.enterPip().then((value) => _controller.play());
     }
  }
  VideoViewController _controller;
  @override
  Widget build(BuildContext context) {
    return Scaffold(
      appBar: AppBar(),
      body: Center(
        child: AspectRatio(
          aspectRatio: 16 / 9,
          child: Container(
            child: NativeVideoView(
              keepAspectRatio: false,
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
                Future.delayed(Duration(seconds: 2), () {
                  _controller.play();
                });
              },
              onPrepared: (controller, info) {
                // controller.play();
              },
              onError: (controller, what, extra, message) {
                print('Player Error ($what | $extra | $message)');
              },
              onCompletion: (controller) {
                print('Video completed');
              },
            ),
          ),
        ),
      ),
    );
  }



}

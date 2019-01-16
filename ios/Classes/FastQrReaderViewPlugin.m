#import "FastQrReaderViewPlugin.h"
#import <AVFoundation/AVFoundation.h>
#import <libkern/OSAtomic.h>

@interface NSError (FlutterError)
@property(readonly, nonatomic) FlutterError *flutterError;
@end

@implementation NSError (FlutterError)
- (FlutterError *)flutterError {
  return [FlutterError errorWithCode:[NSString stringWithFormat:@"Error %d", (int)self.code]
                             message:self.domain
                             details:self.localizedDescription];
}
@end

@interface FMCam : NSObject<FlutterTexture, AVCaptureVideoDataOutputSampleBufferDelegate, FlutterStreamHandler,
AVCaptureMetadataOutputObjectsDelegate>
@property(readonly, nonatomic) int64_t textureId;
@property(nonatomic, copy) void (^onFrameAvailable)(void);
@property(nonatomic) FlutterEventChannel *eventChannel;
@property(nonatomic) FlutterEventSink eventSink;
@property(readonly, nonatomic) AVCaptureSession *captureSession;
@property(readonly, nonatomic) AVCaptureDevice *captureDevice;
@property(readonly, nonatomic) AVCaptureVideoDataOutput *captureVideoOutput;
@property(readonly, nonatomic) AVCaptureInput *captureVideoInput;
@property(readonly, nonatomic) AVCaptureMetadataOutput *captureMetadataOutput;
@property(readonly) CVPixelBufferRef volatile latestPixelBuffer;
@property(readonly, nonatomic) CGSize previewSize;
@property(readonly, nonatomic) CGSize captureSize;
@property(strong, nonatomic) AVAssetWriter *videoWriter;
@property(strong, nonatomic) AVAssetWriterInput *videoWriterInput;
//@property(strong, nonatomic) AVAssetWriterInput *audioWriterInput;
@property(strong, nonatomic) AVAssetWriterInputPixelBufferAdaptor *assetWriterPixelBufferAdaptor;
@property(assign, nonatomic) BOOL isScanning;
@property(strong, nonatomic) FlutterMethodChannel *channel;
@property(strong, nonatomic) NSArray *codeFormats;

- (instancetype)initWithCameraName:(NSString *)cameraName
                  resolutionPreset:(NSString *)resolutionPreset
                     methodChannel:(FlutterMethodChannel *)channel
                       codeFormats:(NSArray *)codeFormats
                             error:(NSError **)error;
- (void)start;
- (void)stop;
@end

@implementation FMCam
- (instancetype)initWithCameraName:(NSString *)cameraName
                  resolutionPreset:(NSString *)resolutionPreset
                     methodChannel:(FlutterMethodChannel *)channel
                       codeFormats:(NSArray *)codeFormats
                             error:(NSError **)error {
    self = [super init];
    NSAssert(self, @"super init cannot be nil");
    _captureSession = [[AVCaptureSession alloc] init];
    AVCaptureSessionPreset preset;
    if ([resolutionPreset isEqualToString:@"high"]) {
        preset = AVCaptureSessionPresetHigh;
    } else if ([resolutionPreset isEqualToString:@"medium"]) {
        preset = AVCaptureSessionPresetMedium;
    } else {
        NSAssert([resolutionPreset isEqualToString:@"low"], @"Unknown resolution preset %@",
                 resolutionPreset);
        preset = AVCaptureSessionPresetLow;
    }
    _captureSession.sessionPreset = preset;
    _captureDevice = [AVCaptureDevice deviceWithUniqueID:cameraName];
    NSError *localError = nil;
    _captureVideoInput =
    [AVCaptureDeviceInput deviceInputWithDevice:_captureDevice error:&localError];
    if (localError) {
        *error = localError;
        return nil;
    }
    CMVideoDimensions dimensions =
    CMVideoFormatDescriptionGetDimensions([[_captureDevice activeFormat] formatDescription]);
    _previewSize = CGSizeMake(dimensions.width, dimensions.height);
    
    _captureVideoOutput = [AVCaptureVideoDataOutput new];
    _captureVideoOutput.videoSettings =
    @{(NSString *)kCVPixelBufferPixelFormatTypeKey : @(kCVPixelFormatType_32BGRA) };
    [_captureVideoOutput setAlwaysDiscardsLateVideoFrames:YES];
    [_captureVideoOutput setSampleBufferDelegate:self queue:dispatch_get_main_queue()];
    
    AVCaptureConnection *connection =
    [AVCaptureConnection connectionWithInputPorts:_captureVideoInput.ports
                                           output:_captureVideoOutput];
    if ([_captureDevice position] == AVCaptureDevicePositionFront) {
        connection.videoMirrored = YES;
    }
    connection.videoOrientation = AVCaptureVideoOrientationPortrait;
    [_captureSession addInputWithNoConnections:_captureVideoInput];
    [_captureSession addOutputWithNoConnections:_captureVideoOutput];
    [_captureSession addConnection:connection];
//    _capturePhotoOutput = [AVCapturePhotoOutput new];
//    [_captureSession addOutput:_capturePhotoOutput];
    self.channel = channel;
    self.codeFormats = codeFormats;
    
    
    dispatch_queue_t dispatchQueue;
    dispatchQueue = dispatch_queue_create("qrDetectorQueue", NULL);
    _captureMetadataOutput = [[AVCaptureMetadataOutput alloc] init];
    [_captureSession addOutput:_captureMetadataOutput];
    
    //    NSLog(@"QR Code: %@", [_captureMetadataOutput availableMetadataObjectTypes]);
    
    NSDictionary<NSString *, AVMetadataObjectType> *availableFormats = [[NSDictionary alloc] initWithObjectsAndKeys:
                                       AVMetadataObjectTypeCode39Code,@"code39",
                                       AVMetadataObjectTypeCode93Code,@"code93",
                                       AVMetadataObjectTypeCode128Code, @"code128",
                                       AVMetadataObjectTypeEAN8Code,  @"ean8",
                                       AVMetadataObjectTypeEAN13Code,@"ean13",
                                       AVMetadataObjectTypeEAN13Code,@"itf",
                                       AVMetadataObjectTypeUPCECode,@"upce",
                                       AVMetadataObjectTypeAztecCode,@"aztec",
                                       AVMetadataObjectTypeDataMatrixCode,@"datamatrix",
                                       AVMetadataObjectTypePDF417Code, @"pdf417",
                                       AVMetadataObjectTypeQRCode, @"qr",
                                       nil];
    
    NSMutableArray<AVMetadataObjectType> *reqFormats = [[NSMutableArray alloc] init];
    
    for (NSString *f in codeFormats) {
        if ([availableFormats valueForKey:f] != nil) {
            [reqFormats addObject:[availableFormats valueForKey:f]];
        }
    }
    
    
    [_captureMetadataOutput setMetadataObjectTypes: reqFormats];
    [_captureMetadataOutput setMetadataObjectsDelegate:self queue:dispatchQueue];
    
    return self;
}

- (void)start {
    [_captureSession startRunning];
}

- (void)stop {
    [_captureSession stopRunning];
}

- (void)captureOutput:(AVCaptureOutput *)output didOutputMetadataObjects:(NSArray<__kindof AVMetadataObject *> *)metadataObjects fromConnection:(AVCaptureConnection *)connection {
    if (metadataObjects != nil && [metadataObjects count] > 0) {
        AVMetadataMachineReadableCodeObject *metadataObj = [metadataObjects objectAtIndex:0];
//        if ([[metadataObj type] isEqualToString:AVMetadataObjectTypeQRCode]) {
            if (_isScanning) {
                [self performSelectorOnMainThread:@selector(stopScanningWithResult:) withObject:[metadataObj stringValue] waitUntilDone:NO];
            }
//        }
    }
}


-(void)stopScanningWithResult:(NSString*)result {
    if (![result  isEqual: @""] && _isScanning) {
        [_channel invokeMethod:@"updateCode" arguments:result];
        _isScanning = false;
    }
}

- (void)stopScanning:(FlutterResult)result {
    _isScanning = false;
}

- (void)startScanning:(FlutterResult)result {
    // Added this delay to avoid encountering race condition
    double delayInSeconds = 0.1;
    dispatch_time_t popTime = dispatch_time(DISPATCH_TIME_NOW, (int64_t)(delayInSeconds * NSEC_PER_SEC));
    dispatch_after(popTime, dispatch_get_main_queue(), ^(void){
        self->_isScanning = true;
    });
}


- (void)captureOutput:(AVCaptureOutput *)output
didOutputSampleBuffer:(CMSampleBufferRef)sampleBuffer
       fromConnection:(AVCaptureConnection *)connection {
    if (output == _captureVideoOutput) {
        CVPixelBufferRef newBuffer = CMSampleBufferGetImageBuffer(sampleBuffer);
        CFRetain(newBuffer);
        CVPixelBufferRef old = _latestPixelBuffer;
        while (!OSAtomicCompareAndSwapPtrBarrier(old, newBuffer, (void **)&_latestPixelBuffer)) {
            old = _latestPixelBuffer;
        }
        if (old != nil) {
            CFRelease(old);
        }
        if (_onFrameAvailable) {
            _onFrameAvailable();
        }
    }
    if (!CMSampleBufferDataIsReady(sampleBuffer)) {
        _eventSink(@{
                     @"event" : @"error",
                     @"errorDescription" : @"sample buffer is not ready. Skipping sample"
                     });
        return;
    }
}

- (void)close {
    [_captureSession stopRunning];
    for (AVCaptureInput *input in [_captureSession inputs]) {
        [_captureSession removeInput:input];
    }
    for (AVCaptureOutput *output in [_captureSession outputs]) {
        [_captureSession removeOutput:output];
    }
}

- (void)dealloc {
    if (_latestPixelBuffer) {
        CFRelease(_latestPixelBuffer);
    }
}

- (CVPixelBufferRef)copyPixelBuffer {
    CVPixelBufferRef pixelBuffer = _latestPixelBuffer;
    while (!OSAtomicCompareAndSwapPtrBarrier(pixelBuffer, nil, (void **)&_latestPixelBuffer)) {
        pixelBuffer = _latestPixelBuffer;
    }
    return pixelBuffer;
}

- (FlutterError *_Nullable)onCancelWithArguments:(id _Nullable)arguments {
    _eventSink = nil;
    return nil;
}

- (FlutterError *_Nullable)onListenWithArguments:(id _Nullable)arguments
                                       eventSink:(nonnull FlutterEventSink)events {
    _eventSink = events;
    return nil;
}

@end

@interface FastQrReaderViewPlugin ()
@property(readonly, nonatomic) NSObject<FlutterTextureRegistry> *registry;
@property(readonly, nonatomic) NSObject<FlutterBinaryMessenger> *messenger;
@property(readonly, nonatomic) FMCam *camera;
@property(readonly, nonatomic) FlutterMethodChannel *channel;
@end

@implementation FastQrReaderViewPlugin

+ (void)registerWithRegistrar:(NSObject<FlutterPluginRegistrar>*)registrar {
  FlutterMethodChannel* channel = [FlutterMethodChannel
      methodChannelWithName:@"fast_qr_reader_view"
            binaryMessenger:[registrar messenger]];
    FastQrReaderViewPlugin *instance = [[FastQrReaderViewPlugin alloc] initWithRegistry:[registrar textures] messenger:[registrar messenger] methodChannel: channel];
    
  [registrar addMethodCallDelegate:instance channel:channel];
}

- (instancetype)initWithRegistry:(NSObject<FlutterTextureRegistry> *)registry
                       messenger:(NSObject<FlutterBinaryMessenger> *)messenger
                   methodChannel:(FlutterMethodChannel *)channel {
    self = [super init];
    NSAssert(self, @"super init cannot be nil");
    _registry = registry;
    _messenger = messenger;
    _channel = channel;
    return self;
}

- (void)handleMethodCall:(FlutterMethodCall *)call result:(FlutterResult)result {
    if ([@"init" isEqualToString:call.method]) {
        if (_camera) {
            [_camera close];
        }
        result(nil);
    } else if ([@"availableCameras" isEqualToString:call.method]) {
        AVCaptureDeviceDiscoverySession *discoverySession = [AVCaptureDeviceDiscoverySession
                                                             discoverySessionWithDeviceTypes:@[ AVCaptureDeviceTypeBuiltInWideAngleCamera ]
                                                             mediaType:AVMediaTypeVideo
                                                             position:AVCaptureDevicePositionUnspecified];
        NSArray<AVCaptureDevice *> *devices = discoverySession.devices;
        NSMutableArray<NSDictionary<NSString *, NSObject *> *> *reply =
        [[NSMutableArray alloc] initWithCapacity:devices.count];
        for (AVCaptureDevice *device in devices) {
            NSString *lensFacing;
            switch ([device position]) {
                    case AVCaptureDevicePositionBack:
                    lensFacing = @"back";
                    break;
                    case AVCaptureDevicePositionFront:
                    lensFacing = @"front";
                    break;
                    case AVCaptureDevicePositionUnspecified:
                    lensFacing = @"external";
                    break;
            }
            [reply addObject:@{
                               @"name" : [device uniqueID],
                               @"lensFacing" : lensFacing,
                               }];
        }
        result(reply);
    } else if ([@"initialize" isEqualToString:call.method]) {
        NSString *cameraName = call.arguments[@"cameraName"];
        NSString *resolutionPreset = call.arguments[@"resolutionPreset"];
        NSArray *formats = call.arguments[@"codeFormats"];
        NSError *error;
        FMCam *cam = [[FMCam alloc] initWithCameraName:cameraName
                                        resolutionPreset:resolutionPreset
                                           methodChannel:_channel
                                             codeFormats: formats
                                                   error:&error];
        if (error) {
            result([error flutterError]);
        } else {
            if (_camera) {
                [_camera close];
            }
            int64_t textureId = [_registry registerTexture:cam];
            _camera = cam;
            cam.onFrameAvailable = ^{
                [_registry textureFrameAvailable:textureId];
            };
            FlutterEventChannel *eventChannel = [FlutterEventChannel
                                                 eventChannelWithName:[NSString
                                                                       stringWithFormat:@"fast_qr_reader_view/cameraEvents%lld",
                                                                       textureId]
                                                 binaryMessenger:_messenger];
            [eventChannel setStreamHandler:cam];
            cam.eventChannel = eventChannel;
            result(@{
                     @"textureId" : @(textureId),
                     @"previewWidth" : @(cam.previewSize.width),
                     @"previewHeight" : @(cam.previewSize.height),
                     @"captureWidth" : @(cam.captureSize.width),
                     @"captureHeight" : @(cam.captureSize.height),
                     });
            [cam start];
        }
    } else {
        NSDictionary *argsMap = call.arguments;
        NSUInteger textureId = ((NSNumber *)argsMap[@"textureId"]).unsignedIntegerValue;
        
        if ([@"dispose" isEqualToString:call.method]) {
            [_registry unregisterTexture:textureId];
            [_camera close];
            result(nil);
        } else if ([@"startScanning" isEqualToString:call.method]) {
            [_camera startScanning:result];
        } else if ([@"stopScanning" isEqualToString:call.method]) {
            [_camera stopScanning:result];
        } else {
            result(FlutterMethodNotImplemented);
        }
    }
}

@end

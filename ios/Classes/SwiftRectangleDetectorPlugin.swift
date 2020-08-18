import Flutter
import UIKit
import VisionKit

public class SwiftRectangleDetectorPlugin: NSObject, FlutterPlugin, VNDocumentCameraViewControllerDelegate {
  var result: FlutterResult? = nil
  public static func register(with registrar: FlutterPluginRegistrar) {
    let channel = FlutterMethodChannel(name: "rectangle_detector", binaryMessenger: registrar.messenger())
    let instance = SwiftRectangleDetectorPlugin()
    registrar.addMethodCallDelegate(instance, channel: channel)
  }

  public func handle(_ call: FlutterMethodCall, result: @escaping FlutterResult) {
    result("iOS " + UIDevice.current.systemVersion)
  }

  public func documentCameraViewControllerDidCancel(_ controller: VNDocumentCameraViewController) {
        UIWindow.key?.rootViewController?.dismiss(animated: true, completion: nil)
  }

  public func documentCameraViewController(_ controller: VNDocumentCameraViewController, didFailWithError error: Error) {
      print("Document Scanner did fail with Error \(error.localizedDescription)")
      UIWindow.key?.rootViewController?.dismiss(animated: true, completion: nil)
  }

  public func documentCameraViewController(_ controller: VNDocumentCameraViewController, didFinishWith scan: VNDocumentCameraScan) {
      UIWindow.key?.rootViewController?.dismiss(animated: true, completion: nil)
  }
}

extension Date {
    func toMillis() -> String {
        let timeStamp =  Int64(self.timeIntervalSince1970 * 1000)
        return String(timeStamp)
    }
}

extension UIWindow {
    static var key: UIWindow? {
        if #available(iOS 13, *) {
            return UIApplication.shared.windows.first { $0.isKeyWindow }
        } else {
            return UIApplication.shared.keyWindow
        }
    }
}

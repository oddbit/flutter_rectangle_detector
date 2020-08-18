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
    switch call.method {
        case "getPlatformVersion":
          result("iOS " + UIDevice.current.systemVersion)
          break
        case "showDocumentCapture":
          self.result = result
          
          let documentCamera: VNDocumentCameraViewController = VNDocumentCameraViewController()
          documentCamera.delegate = self
          UIWindow.key?.rootViewController?.present(documentCamera, animated: true, completion: nil)
          break
        default:
          result(FlutterMethodNotImplemented)
    }
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
      let pageCount = scan.pageCount
      let image = scan.imageOfPage(at: pageCount - 1)
      let filename = "\(Date().toMillis()).png"
      let path = saveImageToDocumentsDirectory(image: image, withName: filename)
      if let flutterResult = self.result{
          flutterResult(path)
      }
      self.result = nil
  }
  func saveImageToDocumentsDirectory(image: UIImage, withName: String) -> String? {
      if let data = image.pngData() {
          let dirPath = getDocumentDirectoryPath()
          let imageFileUrl = URL(fileURLWithPath: dirPath.appendingPathComponent(withName) as String)
          do {
              try data.write(to: imageFileUrl)
              print("Successfully saved image at path: \(imageFileUrl.path)")
              return imageFileUrl.path
          } catch {
              print("Error saving image: \(error)")
          }
      }
      return nil
  }

  func getDocumentDirectoryPath() -> NSString {
      let paths = NSSearchPathForDirectoriesInDomains(.documentDirectory, .userDomainMask, true)
      let documentsDirectory = paths[0]
      return documentsDirectory as NSString
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

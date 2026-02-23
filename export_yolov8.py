from ultralytics import YOLO

# Load a pre-trained YOLOv8n model
model = YOLO('yolov8n.pt')

# Export the model to TFLite format
# We specify imgsz=320 to match the current app's input size for speed
# int8=False because we want float32 for now without requiring a representative dataset
model.export(format='tflite', imgsz=320, int8=False)

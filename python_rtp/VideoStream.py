# VideoStream.py
import re

class VideoStream:
    def __init__(self, filename):
        self.filename = filename
        try:
            with open(filename, 'rb') as f:
                self.data = f.read()
        except:
            raise IOError

        # Tìm tất cả frame JPEG theo FFD8...FFD9
        self.frames = re.findall(rb'\xff\xd8.*?\xff\xd9', self.data, re.DOTALL)
        self.frameNum = 0

        print("[VideoStream] Loaded", len(self.frames), "frames from", filename)

    def nextFrame(self):
        """Return next JPEG frame by index."""
        if self.frameNum >= len(self.frames):
            return None
        frame = self.frames[self.frameNum]
        self.frameNum += 1
        return frame

    def frameNbr(self):
        return self.frameNum
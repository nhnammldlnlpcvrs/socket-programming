# ServerWorker.py

from random import randint
import sys, traceback, threading, socket, time

from RtpPacket import RtpPacket

MAX_PAYLOAD = 1400
SOF = b'\xff\xd8'   # JPEG Start
EOF = b'\xff\xd9'   # JPEG End

class ServerWorker:
    SETUP = 'SETUP'
    PLAY = 'PLAY'
    PAUSE = 'PAUSE'
    TEARDOWN = 'TEARDOWN'

    INIT = 0
    READY = 1
    PLAYING = 2
    state = INIT

    OK_200 = 0
    FILE_NOT_FOUND_404 = 1
    CON_ERR_500 = 2

    clientInfo = {}

    def __init__(self, clientInfo):
        self.clientInfo = clientInfo

    def run(self):
        threading.Thread(target=self.recvRtspRequest).start()

    def recvRtspRequest(self):
        connSocket = self.clientInfo['rtspSocket'][0]
        while True:
            data = connSocket.recv(256)
            if data:
                print("RTSP Data:\n" + data.decode("utf-8"))
                self.processRtspRequest(data.decode("utf-8"))

    def processRtspRequest(self, data):
        request = data.split('\n')
        line1 = request[0].split(' ')
        requestType = line1[0]
        filename = line1[1]
        seq = request[1].split(' ')

        # SETUP 
        if requestType == self.SETUP:
            if self.state == self.INIT:
                print("processing SETUP\n")
                try:
                    self.loadMJPEG(filename)
                    self.clientInfo['pktSeq'] = 0
                    self.clientInfo['frameIndex'] = 0
                    self.state = self.READY
                except IOError:
                    self.replyRtsp(self.FILE_NOT_FOUND_404, seq[1])
                    return

                self.clientInfo['session'] = randint(100000, 999999)
                self.replyRtsp(self.OK_200, seq[1])

                try:
                    self.clientInfo['rtpPort'] = int(request[2].split('=')[1].strip())
                except:
                    self.clientInfo['rtpPort'] = 25000

        # PLAY 
        elif requestType == self.PLAY:
            if self.state == self.READY:
                print("processing PLAY\n")
                self.state = self.PLAYING

                self.clientInfo["rtpSocket"] = socket.socket(socket.AF_INET, socket.SOCK_DGRAM)
                self.replyRtsp(self.OK_200, seq[1])

                self.clientInfo['event'] = threading.Event()
                self.clientInfo['worker'] = threading.Thread(target=self.sendRtp)
                self.clientInfo['worker'].start()

        # PAUSE 
        elif requestType == self.PAUSE:
            if self.state == self.PLAYING:
                print("processing PAUSE\n")
                self.state = self.READY
                self.clientInfo['event'].set()
                self.replyRtsp(self.OK_200, seq[1])

        # TEARDOWN 
        elif requestType == self.TEARDOWN:
            print("processing TEARDOWN\n")
            if 'event' in self.clientInfo:
                self.clientInfo['event'].set()
            self.replyRtsp(self.OK_200, seq[1])

            if 'rtpSocket' in self.clientInfo:
                try: self.clientInfo['rtpSocket'].close()
                except: pass

    # MJPEG READER – RAW FFD8 / FFD9 PARSING
    def loadMJPEG(self, filename):
        """Load MJPEG file & split all frames by JPEG markers."""
        with open(filename, "rb") as f:
            data = f.read()

        frames = []
        i = 0
        while True:
            start = data.find(SOF, i)
            if start < 0:
                break
            end = data.find(EOF, start)
            if end < 0:
                break
            end += 2  # include FFD9
            frames.append(data[start:end])
            i = end

        if len(frames) == 0:
            raise IOError("No JPEG frames found!")

        print(f"Loaded {len(frames)} frames from MJPEG.")
        self.clientInfo['frames'] = frames

    # RTP SENDER
    def sendRtp(self):
        """Send RTP packets repeatedly."""
        while True:
            self.clientInfo['event'].wait(0.04)  # 25 FPS

            if self.clientInfo['event'].isSet():
                break

            frames = self.clientInfo['frames']
            idx = self.clientInfo['frameIndex']

            frame = frames[idx]
            frameTimestamp = idx  # Simple timestamp = frame index

            # Move to next frame (loop)
            self.clientInfo['frameIndex'] = (idx + 1) % len(frames)

            # Fragment and send
            self.sendFrame(frame, frameTimestamp)

    def sendFrame(self, frame_bytes, timestamp):
        """Fragment frame into RTP packets."""
        total = len(frame_bytes)
        num = (total + MAX_PAYLOAD - 1) // MAX_PAYLOAD

        address = self.clientInfo['rtspSocket'][1][0]
        port = int(self.clientInfo['rtpPort'])

        for i in range(num):
            start = i * MAX_PAYLOAD
            end = start + MAX_PAYLOAD
            chunk = frame_bytes[start:end]

            marker = 1 if i == num - 1 else 0

            # Increase packet seq num
            self.clientInfo['pktSeq'] = (self.clientInfo['pktSeq'] + 1) & 0xFFFF

            packet = self.makeRtp(chunk, timestamp, self.clientInfo['pktSeq'], marker)
            self.clientInfo['rtpSocket'].sendto(packet, (address, port))

    def makeRtp(self, payload_chunk, timestamp, seqnum, marker):
        version = 2
        padding = 0
        extension = 0
        cc = 0
        pt = 26     # MJPEG
        ssrc = 12345

        pkt = RtpPacket()
        pkt.encode(
            version, padding, extension, cc,
            seqnum,
            timestamp,   # correct timestamp
            marker,
            pt,
            ssrc,
            payload_chunk
        )
        return pkt.getPacket()

    def replyRtsp(self, code, seq):
        if code == self.OK_200:
            reply = 'RTSP/1.0 200 OK\nCSeq: ' + str(seq) + '\nSession: ' + str(self.clientInfo['session'])
            connSocket = self.clientInfo['rtspSocket'][0]
            connSocket.send(reply.encode())
        elif code == self.FILE_NOT_FOUND_404:
            print("404 NOT FOUND")
        elif code == self.CON_ERR_500:
            print("500 CONNECTION ERROR")
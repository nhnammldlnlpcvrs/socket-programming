# Client.py
from tkinter import *
import tkinter.messagebox
from PIL import Image, ImageTk
import socket, threading, sys, traceback, os

from RtpPacket import RtpPacket

CACHE_FILE_NAME = "cache-"
CACHE_FILE_EXT = ".jpg"

class Client:
	INIT = 0
	READY = 1
	PLAYING = 2
	state = INIT
	
	SETUP = 0
	PLAY = 1
	PAUSE = 2
	TEARDOWN = 3
	
	def __init__(self, master, serveraddr, serverport, rtpport, filename):
		self.master = master
		self.master.protocol("WM_DELETE_WINDOW", self.handler)
		self.createWidgets()
		self.serverAddr = serveraddr
		self.serverPort = int(serverport)
		self.rtpPort = int(rtpport)
		self.fileName = filename
		self.rtspSeq = 0
		self.sessionId = 0
		self.requestSent = -1
		self.teardownAcked = 0
		self.connectToServer()
		self.frameNbr = 0
		
	def createWidgets(self):
		self.setup = Button(self.master, width=20, padx=3, pady=3)
		self.setup["text"] = "Setup"
		self.setup["command"] = self.setupMovie
		self.setup.grid(row=1, column=0, padx=2, pady=2)
		
		self.start = Button(self.master, width=20, padx=3, pady=3)
		self.start["text"] = "Play"
		self.start["command"] = self.playMovie
		self.start.grid(row=1, column=1, padx=2, pady=2)
		
		self.pause = Button(self.master, width=20, padx=3, pady=3)
		self.pause["text"] = "Pause"
		self.pause["command"] = self.pauseMovie
		self.pause.grid(row=1, column=2, padx=2, pady=2)
		
		self.teardown = Button(self.master, width=20, padx=3, pady=3)
		self.teardown["text"] = "Teardown"
		self.teardown["command"] =  self.exitClient
		self.teardown.grid(row=1, column=3, padx=2, pady=2)
		
		self.label = Label(self.master, height=19)
		self.label.grid(row=0, column=0, columnspan=4, sticky=W+E+N+S, padx=5, pady=5) 
	
	def setupMovie(self):
		if self.state == self.INIT:
			self.sendRtspRequest(self.SETUP)
	
	def exitClient(self):
		self.sendRtspRequest(self.TEARDOWN)
		self.master.destroy()
		try:
			os.remove(CACHE_FILE_NAME + str(self.sessionId) + CACHE_FILE_EXT)
		except:
			pass

	def pauseMovie(self):
		if self.state == self.PLAYING:
			self.sendRtspRequest(self.PAUSE)
	
	def playMovie(self):
		if self.state == self.READY:
			# Prepare assemble buffers
			self.recvBuffer = bytearray()
			self.assemblingTimestamp = None
			threading.Thread(target=self.listenRtp).start()
			self.playEvent = threading.Event()
			self.playEvent.clear()
			self.sendRtspRequest(self.PLAY)
	
	def listenRtp(self):
		"""Listen for RTP packets and assemble fragments into full frames."""
		while True:
			try:
				# increase recv buffer to max UDP packet size
				data = self.rtpSocket.recv(65535)
				if data:
					rtpPacket = RtpPacket()
					rtpPacket.decode(data)
				
					currTimestamp = rtpPacket.timestamp()
					marker = rtpPacket.marker()
					payload = rtpPacket.getPayload()
				
					# If starting a new frame (different timestamp), reset buffer
					if self.assemblingTimestamp is None or currTimestamp != self.assemblingTimestamp:
						self.assemblingTimestamp = currTimestamp
						self.recvBuffer = bytearray()
				
					# Append payload
					self.recvBuffer.extend(payload)
				
					# If marker == 1 -> last fragment of frame
					if marker == 1:
						# Completed frame
						full_frame = bytes(self.recvBuffer)
						# Update frame counter (we treat each completed frame as a new frame)
						self.frameNbr += 1
						# Write and display
						self.updateMovie(self.writeFrame(full_frame))
						# Reset assembly
						self.assemblingTimestamp = None
						self.recvBuffer = bytearray()
			except:
				# Stop listening upon requesting PAUSE or TEARDOWN
				if hasattr(self, 'playEvent') and self.playEvent.isSet():
					break
				
				# Upon receiving ACK for TEARDOWN request,
				# close the RTP socket
				if self.teardownAcked == 1:
					try:
						self.rtpSocket.shutdown(socket.SHUT_RDWR)
						self.rtpSocket.close()
					except:
						pass
					break
					
	def writeFrame(self, data):
		cachename = CACHE_FILE_NAME + str(self.sessionId) + CACHE_FILE_EXT
		with open(cachename, "wb") as file:
			file.write(data)
		return cachename
	
	def updateMovie(self, imageFile):
		photo = ImageTk.PhotoImage(Image.open(imageFile))
		self.label.configure(image=photo, height=288)
		self.label.image = photo
		
	def connectToServer(self):
		self.rtspSocket = socket.socket(socket.AF_INET, socket.SOCK_STREAM)
		try:
			self.rtspSocket.connect((self.serverAddr, self.serverPort))
		except:
			tkinter.messagebox.showwarning('Connection Failed', f'Cannot connect to {self.serverAddr}:{self.serverPort}')
	
	def sendRtspRequest(self, requestCode):
		"""Send RTSP request to the server."""
		# SETUP
		if requestCode == self.SETUP and self.state == self.INIT:
			threading.Thread(target=self.recvRtspReply).start()

			self.rtspSeq += 1
			request = f"SETUP {self.fileName} RTSP/1.0\nCSeq: {self.rtspSeq}\nTransport: RTP/UDP; client_port={self.rtpPort}"
			self.requestSent = self.SETUP

		# PLAY
		elif requestCode == self.PLAY and self.state == self.READY:
			self.rtspSeq += 1
			request = f"PLAY {self.fileName} RTSP/1.0\nCSeq: {self.rtspSeq}\nSession: {self.sessionId}"
			self.requestSent = self.PLAY

		# PAUSE
		elif requestCode == self.PAUSE and self.state == self.PLAYING:
			self.rtspSeq += 1
			request = f"PAUSE {self.fileName} RTSP/1.0\nCSeq: {self.rtspSeq}\nSession: {self.sessionId}"
			self.requestSent = self.PAUSE

		# TEARDOWN
		elif requestCode == self.TEARDOWN and self.state != self.INIT:
			self.rtspSeq += 1
			request = f"TEARDOWN {self.fileName} RTSP/1.0\nCSeq: {self.rtspSeq}\nSession: {self.sessionId}"
			self.requestSent = self.TEARDOWN

		else:
			return

		self.rtspSocket.send(request.encode())
		print("\nSent RTSP request:\n" + request)

	
	def recvRtspReply(self):
		while True:
			reply = self.rtspSocket.recv(1024)
			if reply:
				self.parseRtspReply(reply.decode("utf-8"))

			if self.requestSent == self.TEARDOWN:
				self.rtspSocket.shutdown(socket.SHUT_RDWR)
				self.rtspSocket.close()
				break
	
	def parseRtspReply(self, data):
		lines = data.split('\n')
		seqNum = int(lines[1].split(' ')[1])
		
		if seqNum == self.rtspSeq:
			session = int(lines[2].split(' ')[1])

			if self.sessionId == 0:
				self.sessionId = session
			
			if self.sessionId == session:
				code = int(lines[0].split(' ')[1])

				if code == 200:
					if self.requestSent == self.SETUP:
						self.state = self.READY
						self.openRtpPort()

					elif self.requestSent == self.PLAY:
						self.state = self.PLAYING

					elif self.requestSent == self.PAUSE:
						self.state = self.READY
						self.playEvent.set()

					elif self.requestSent == self.TEARDOWN:
						self.state = self.INIT
						self.teardownAcked = 1
	
	def openRtpPort(self):
		"""Open RTP socket."""
		self.rtpSocket = socket.socket(socket.AF_INET, socket.SOCK_DGRAM)
		self.rtpSocket.settimeout(0.5)

		try:
			self.rtpSocket.bind(("", self.rtpPort))
		except:
			tkinter.messagebox.showwarning("Unable to Bind", f"Cannot bind PORT={self.rtpPort}")

	def handler(self):
		self.pauseMovie()
		if tkinter.messagebox.askokcancel("Quit?", "Are you sure you want to quit?"):
			self.exitClient()
		else:
			self.playMovie()
# RtpPacket.py
import sys
from time import time
HEADER_SIZE = 12

class RtpPacket:
	header = bytearray(HEADER_SIZE)
	
	def __init__(self):
		self.header = bytearray(HEADER_SIZE)
		self.payload = bytearray()
		
	def encode(self, version, padding, extension, cc, seqnum, timestamp, marker, pt, ssrc, payload):
		"""Encode the RTP packet with header fields and payload.
		- seqnum: 16-bit sequence number (per packet)
		- timestamp: 32-bit timestamp (we'll use frame number for grouping fragments)
		- marker: 1 if last packet of a frame, 0 otherwise
		"""
		# header byte 0: V(2),P(1),X(1),CC(4)
		self.header[0] = ((version & 0x03) << 6) | ((padding & 0x01) << 5) | ((extension & 0x01) << 4) | (cc & 0x0F)
		
		# header byte 1: M(1), PT(7)
		self.header[1] = ((marker & 0x01) << 7) | (pt & 0x7F)
		
		# sequence number: bytes 2-3
		self.header[2] = (seqnum >> 8) & 0xFF
		self.header[3] = seqnum & 0xFF
		
		# timestamp: bytes 4-7 (32 bits)
		self.header[4] = (timestamp >> 24) & 0xFF
		self.header[5] = (timestamp >> 16) & 0xFF
		self.header[6] = (timestamp >> 8) & 0xFF
		self.header[7] = timestamp & 0xFF
		
		# SSRC: bytes 8-11 (32 bits)
		self.header[8]  = (ssrc >> 24) & 0xFF
		self.header[9]  = (ssrc >> 16) & 0xFF
		self.header[10] = (ssrc >> 8) & 0xFF
		self.header[11] = ssrc & 0xFF
		
		# payload
		self.payload = payload if isinstance(payload, (bytes, bytearray)) else bytes(payload)
		
	def decode(self, byteStream):
		"""Decode the RTP packet."""
		self.header = bytearray(byteStream[:HEADER_SIZE])
		self.payload = byteStream[HEADER_SIZE:]
	
	def version(self):
		"""Return RTP version."""
		return int(self.header[0] >> 6)
	
	def seqNum(self):
		"""Return sequence (packet) number."""
		seqNum = (self.header[2] << 8) | self.header[3]
		return int(seqNum)
	
	def timestamp(self):
		"""Return timestamp (32-bit)."""
		timestamp = (self.header[4] << 24) | (self.header[5] << 16) | (self.header[6] << 8) | self.header[7]
		return int(timestamp)
	
	def payloadType(self):
		"""Return payload type."""
		pt = self.header[1] & 0x7F
		return int(pt)
	
	def marker(self):
		"""Return marker bit (1 if last packet of frame)."""
		return (self.header[1] >> 7) & 0x01
	
	def getPayload(self):
		"""Return payload bytes."""
		return self.payload
		
	def getPacket(self):
		"""Return RTP packet (header + payload)."""
		return bytes(self.header) + bytes(self.payload)
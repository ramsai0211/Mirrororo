
const WebSocket = require("ws");
const http = require("http");

const server = http.createServer((req, res) => {
  res.writeHead(200, { "Content-Type": "text/plain" });
  res.end("Mirrororo server is running!");
});

const wss = new WebSocket.Server({ server });
const rooms = new Map();

wss.on("connection", (socket) => {
  socket.on("message", (raw) => {
    let msg;
    try {
      msg = JSON.parse(raw.toString());
    } catch {
      return;
    }

    if (msg.type === "join" && typeof msg.room === "string") {
      const room = msg.room.trim().toUpperCase();
      if (!room || room.length > 40) return;

      socket.room = room;
      if (!rooms.has(room)) rooms.set(room, new Set());
      rooms.get(room).add(socket);

      socket.send(JSON.stringify({ type: "joined", room }));

      for (const peer of rooms.get(room)) {
        if (peer !== socket && peer.readyState === 1) {
          peer.send(JSON.stringify({ type: "peer-joined" }));
        }
      }
      return;
    }

    if (!socket.room) return;
    for (const peer of rooms.get(socket.room) || []) {
      if (peer !== socket && peer.readyState === 1) {
        peer.send(raw.toString());
      }
    }
  });

  socket.on("close", () => {
    if (!socket.room) return;
    const peers = rooms.get(socket.room);
    if (!peers) return;
    peers.delete(socket);
    for (const peer of peers) {
      if (peer.readyState === 1) {
        peer.send(JSON.stringify({ type: "peer-left" }));
      }
    }
    if (peers.size === 0) rooms.delete(socket.room);
  });
});

const PORT = process.env.PORT || 8080;
server.listen(PORT, "0.0.0.0", () => {
  console.log("Mirrororo server running on port " + PORT);
});

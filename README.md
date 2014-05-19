Generic TCP Driver
==================

The generic TCP driver connects network devices that use a custom protocol to the Ignition(R) OPC-UA server. The communication protocol is configurable for each device. This driver does not poll data but expects the connected device to send messages on its own.

The Generic TCP Driver module adds two new drivers to the Ignition TM OPC-UA Server:
- Generic TCP Client Driver
The client driver connects actively to a remote device.
- Generic TCP Server Driver
The server driver is as a passive listener. It opens a tcp-port on the server machine and waits for remote devices to establish a connection. Each driver instance allows multiple remote devices to connect.
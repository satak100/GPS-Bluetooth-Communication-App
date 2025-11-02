# 📱 GPS Bluetooth Transmitter

An Android application that fetches your phone's GPS coordinates and transmits them over Bluetooth to a microcontroller such as the **ATmega32** via an **HC-06 Bluetooth module**.

---

## 🚀 Features

- 🌍 Real-time GPS location using Google's Fused Location Provider
- 🔗 Connects to Bluetooth HC-06 module (SPP serial communication)
- 📤 Sends coordinates in a simple, parseable format
- 🔁 Manual and automatic transmission modes
- 📋 Clean UI with logs and status indicators
- 🔐 Runtime permission handling for Android 6.0+
- 🎯 Configurable auto-send intervals
- 📱 Modern Material Design UI

---

## 🛠️ System Requirements

| Component           | Specification                  |
|---------------------|--------------------------------|
| Android Version     | Android 8.0+ (API level 26+)   |
| Target Device       | Android phone with GPS & BT    |
| Microcontroller     | ATmega32                       |
| Bluetooth Module    | HC-06 (SPP, 9600 baud)         |
| IDE (for dev)       | Android Studio (latest)        |

---

## 📦 App Architecture

| Component        | Responsibility                             |
|------------------|---------------------------------------------|
| `MainActivity`   | UI handling, user input, timer setup         |
| `LocationService`| Fetches GPS coordinates using FusedLocationProvider |
| `BluetoothService`| Manages Bluetooth connection and data I/O  |

---

## 📡 Data Format

The app sends the following string to the HC-06:

```
LAT:latitude,LON:longitude,ACC:accuracy\n
```

Example:
```
LAT:37.7749,LON:-122.4194,ACC:5.0
```

---

## 🔧 Setup Instructions

### 1. Hardware Setup
1. Connect HC-06 to your microcontroller:
   - HC-06 VCC → 3.3V or 5V
   - HC-06 GND → Ground
   - HC-06 TXD → Microcontroller RX pin
   - HC-06 RXD → Microcontroller TX pin

2. Find your HC-06 MAC address:
   - Pair with your phone in Bluetooth settings
   - Note down the MAC address (format: XX:XX:XX:XX:XX:XX)

### 2. App Configuration
1. Open `BluetoothService.kt`
2. Replace the MAC address in line 15:
   ```kotlin
   const val HC06_MAC_ADDRESS = "98:D3:31:FB:48:F6" // Replace with your HC-06 MAC
   ```

### 3. Build and Install
1. Open the project in Android Studio
2. Connect your Android device with USB debugging enabled
3. Build and run the app

---

## 📱 How to Use

### First Time Setup
1. Launch the app
2. Grant location and Bluetooth permissions when prompted
3. Enable Bluetooth if not already enabled

### Connecting to HC-06
1. Pair your phone with HC-06 in Android Bluetooth settings first
2. In the app, tap "Connect Bluetooth"
3. Wait for connection confirmation

### Sending Location Data
**Manual Mode:**
- Tap "Send Location" to send current GPS coordinates once

**Auto Mode:**
1. Toggle "Auto Send" switch
2. Set desired interval in seconds (default: 5 seconds)
3. App will automatically send location data at specified intervals

### Monitoring
- View real-time GPS coordinates in the Location section
- Check connection status in the Status section
- Monitor all activities in the Logs section

---

## 🔐 Permissions

The app requires the following permissions:
- **Location (Fine & Coarse)**: To access GPS coordinates
- **Bluetooth**: To connect and communicate with HC-06
- **Bluetooth Connect/Scan** (Android 12+): For Bluetooth operations

All permissions are requested at runtime with proper explanations.

---

## 📋 Microcontroller Code Example (ATmega32)

Here's a simple example for receiving data on ATmega32:

```c
#include <avr/io.h>
#include <util/delay.h>
#include <string.h>

void uart_init(void) {
    // Set baud rate to 9600
    UBRRH = 0;
    UBRRL = 51; // For 8MHz clock
    
    // Enable receiver and transmitter
    UCSRB = (1<<RXEN) | (1<<TXEN);
    
    // Set frame format: 8 data bits, 1 stop bit
    UCSRC = (1<<URSEL) | (3<<UCSZ0);
}

char uart_receive(void) {
    // Wait for data to be received
    while (!(UCSRA & (1<<RXC)));
    
    // Return received data
    return UDR;
}

void uart_transmit(char data) {
    // Wait for empty transmit buffer
    while (!(UCSRA & (1<<UDRE)));
    
    // Put data into buffer
    UDR = data;
}

int main(void) {
    uart_init();
    char buffer[100];
    int index = 0;
    
    while (1) {
        char received = uart_receive();
        
        if (received == '\n') {
            buffer[index] = '\0';
            
            // Parse GPS data
            // Example: "LAT:37.7749,LON:-122.4194,ACC:5.0"
            
            // Echo back received data
            for (int i = 0; i < index; i++) {
                uart_transmit(buffer[i]);
            }
            uart_transmit('\n');
            
            index = 0;
        } else {
            buffer[index++] = received;
            if (index >= 99) index = 0; // Prevent overflow
        }
    }
    
    return 0;
}
```

---

## 🛠️ Building from Source

### Prerequisites
- Android Studio Arctic Fox or later
- Android SDK 34
- Kotlin 1.9.0+
- Gradle 8.2+

### Dependencies
- Google Play Services Location: 21.0.1
- Material Components: 1.11.0
- AndroidX libraries

### Build Steps
1. Clone this repository
2. Open in Android Studio
3. Sync project with Gradle files
4. Update HC-06 MAC address in BluetoothService.kt
5. Build and run

---

## 🐛 Troubleshooting

### Common Issues

**Bluetooth Connection Fails:**
- Ensure HC-06 is paired in Android Bluetooth settings
- Check MAC address in BluetoothService.kt
- Verify HC-06 is powered and in pairing mode

**No GPS Data:**
- Enable location services in Android settings
- Grant location permissions to the app
- Test outdoors for better GPS signal

**Permission Denied:**
- Go to Android Settings → Apps → GPS Bluetooth Transmitter → Permissions
- Enable all required permissions

**Auto-send Not Working:**
- Ensure Bluetooth is connected
- Check if auto-send switch is enabled
- Verify interval is set correctly

---

## 🔄 Data Flow

```
GPS Sensor → FusedLocationProvider → LocationService → MainActivity → BluetoothService → HC-06 → Microcontroller
```

---

## 📊 Technical Specifications

- **GPS Update Rate**: 5 seconds (configurable)
- **Bluetooth Protocol**: SPP (Serial Port Profile)
- **Data Rate**: 9600 baud
- **Location Accuracy**: Typically 3-5 meters
- **Auto-send Range**: 1-999 seconds
- **Supported Android Versions**: 8.0 (API 26) to 14 (API 34)

---

## 📄 License

This project is open source. Feel free to modify and distribute according to your needs.

---

## 🤝 Contributing

1. Fork the repository
2. Create a feature branch
3. Make your changes
4. Test thoroughly
5. Submit a pull request

---

## 📞 Support

For issues or questions:
1. Check the troubleshooting section above
2. Verify hardware connections
3. Test with a simple Bluetooth terminal app first
4. Check Android device logs for detailed error messages

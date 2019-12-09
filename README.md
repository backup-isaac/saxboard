# saxboard - Isaac Weintraub, Matt Zilvetti

Trick out your skateboard! Our project embeds an ESP32 onto a skateboard to provide PWM control of lights for underglow
as well as an accelerometer and distance measurements.
Connect it to the Android app to control the skateboard and plot your data.

## Setup

[ hardware schematic here ]

To obtain the code for both the skateboard and Android app, clone this repository.

### Programming the Skateboard
This project was developed using the Arduino IDE. It is designed for a NodeMCU-32S, so ensure that your board is correctly configured in the IDE.
Third-party libraries on which this project depends are included as submodules in the `esp32/third_party_libraries` directory.
Make sure you have acquired these submodules (e.g. clone the repo with `--recurse-submodules` or use `git submodule update --init`),
and then copy each of them into your Arduino libraries directory, for instance `~/Documents/Arduino/libraries`.

To program the board, simply open the `esp32/saxboard` sketch, connect to the NodeMCU-32S via USB, and flash.
macOS users may need to install a USB to UART driver from https://www.silabs.com/products/development-tools/software/usb-to-uart-bridge-vcp-drivers.

### Programming the Android App
Open the `android/` project directory in Android Studio. Then build the application and install it onto an Android device.
The app requires a minimum API level of 21 (Lollipop, version 5.0), although it targets and has been tested on API level 27 (Oreo, version 8.1).
Building it also requires Kotlin version `1.3.50`.

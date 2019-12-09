# saxboard
#### Isaac Weintraub, Matt Zilvetti

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

### Installing the Android App
Open the `android/` project directory in Android Studio. Then build the application and install it onto an Android device.
The app requires a minimum API level of 21 (Lollipop, version 5.0), although it targets and has been tested on API level 27 (Oreo, version 8.1).
Building it also requires Kotlin version `1.3.50`.

## Usage
1. Turn on the skateboard by inserting the batteries.
2. Press the "Search" button to search for and pair with the skateboard. This has no effect if your phone is already paired with the skateboard.
3. Press the "Connect" button to connect to the skateboard. At this point you may use any part of the app.
4. The "Disconnect" button will disconnect you from the skateboard.

## Features
Switch between tabs of the app by swiping or tapping on the labels at the top of the screen.
![](https://github.gatech.edu/iweintraub3/saxboard/blob/master/control.png)

- The skateboard has two sets of RGB LED strips. For each LED strip, tap the colored rectangle to bring up a color picker that you can use to select the desired color. Press "Set Color" to set the color. The "Enable"/"Disable" button does what it says.
- (WIP) The skateboard has installed speakers that will be able to play songs off of an SD card. Once you connect to the skateboard, the drop-down menu will be populated with the songs that the SD card contains. Press the "Play"/"Stop" button to perform the corresponding action. There is also a volume adjustment slider.

![](https://github.gatech.edu/iweintraub3/saxboard/blob/master/acceleration.png)

- The skateboard contains an accelerometer that automatically transmits data to the app, which plots it over time. 

![](https://github.gatech.edu/iweintraub3/saxboard/blob/master/distance.png)

- The skateboard also has a Hall effect sensor measuring wheel rotation. This measurement is combined with accelerometer data to show distance travelled.


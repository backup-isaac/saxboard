#include "BluetoothSerial.h"
#include "freertos/queue.h"
#include "freertos/task.h"
#include <Wire.h>
#include <SPI.h>
#include <SparkFunLSM9DS1.h>
#include <stdint.h>
#include <stdbool.h>
#include <string.h>
#include "FastLED.h"
#include <math.h>

#if !defined(CONFIG_BT_ENABLED) || !defined(CONFIG_BLUEDROID_ENABLED)
#error Bluetooth is not enabled! Please run `make menuconfig` to and enable it
#endif

#define STACK_DEPTH 4096 
#define BUF_SIZE 64

uint8_t btReceiveBuf[BUF_SIZE];

QueueHandle_t btSendQueue;
QueueHandle_t ledCmdQueue;
QueueHandle_t audioCommandQueue;

typedef enum {
  WAITING,
  PACKET_BEGUN, 
  ID_RECEIVED
} parser_state;

typedef enum {
  UNRECOGNIZED,
  LEFT_LED_COLOR,
  RIGHT_LED_COLOR,
  LEFT_LED_DISABLE,
  RIGHT_LED_DISABLE,
  LEFT_LED_ENABLE,
  RIGHT_LED_ENABLE,
  PLAY_SONG,
  STOP_SONG,
  DIR_LISTING,
  LEFT_LED_PATTERN,
  RIGHT_LED_PATTERN,
} packet_type;

typedef struct {
  uint8_t flags; // bit 0 = right led (1) or left led (0); bit 1 set -> disable command; bit 2 set -> enable command; bit 3 set -> color command; bit 4 set -> pattern command
  uint8_t red; // only inspect the colors if bit 3 set
  uint8_t green;
  uint8_t blue;
  int8_t pattern; // only inspect the pattern if bit 4 is set
} led_command;

typedef struct {
  uint8_t flags; // bit 0 set -> play command; bit 1 set -> stop command; bit 2 set -> directory list command
  char *song; // only inspect song if bit 0 set
} audio_command;

typedef struct {
  uint8_t len;
  char *str;
} tx_packet;

void bluetoothThread(void *args) {
  char packetId[4];
  packetId[3] = 0;
  uint8_t packetLength = 0;
  uint8_t packetIdIdx = 0;
  uint8_t packetBuffer[32];
  char songBuf[32];
  uint8_t packetBufferIdx = 0;
  bool packetReady = false;
  parser_state ps = WAITING;
  packet_type pt = UNRECOGNIZED;
  BluetoothSerial bt;
  bt.begin("ESP32test");
  Serial.println("The device started, now you can pair it with bluetooth!");
  for (;;) {
    while (bt.available()) {
      uint8_t recv = bt.read();
      Serial.printf("Received 0x%x, state %d\n", recv, ps);
      if (ps == WAITING) {
        if (recv == '!') {
          ps = PACKET_BEGUN;
          packetIdIdx = 0;
          pt = UNRECOGNIZED;
        } 
      } else if (ps == PACKET_BEGUN) {
        packetId[packetIdIdx++] = recv;
        if (packetIdIdx == 3) {
          switch (packetId[0]) {
            case 'D':
              if (packetId[1] == 'I' && packetId[2] == 'R') {
                pt = DIR_LISTING;
                packetLength = 0;
              }
              break;
            case 'L':
              switch (packetId[1]) {
                case 'C':
                  if (packetId[2] == 'L') {
                    pt = LEFT_LED_COLOR;
                    packetLength = 3;
                  } else if (packetId[2] == 'R') {
                    pt = RIGHT_LED_COLOR;
                    packetLength = 3;
                  }
                  break;
                case 'D':
                  if (packetId[2] == 'L') {
                    pt = LEFT_LED_DISABLE;
                    packetLength = 0;
                  } else if (packetId[2] == 'R') {
                    pt = RIGHT_LED_DISABLE;
                    packetLength = 0;
                  }
                  break;
                case 'E':
                  if (packetId[2] == 'L') {
                    pt = LEFT_LED_ENABLE;
                    packetLength = 0;
                  } else if (packetId[2] == 'R') {
                    pt = RIGHT_LED_ENABLE;
                    packetLength = 0;
                  }
                  break;
                case 'P':
                  if (packetId[2] == 'L') {
                    pt = LEFT_LED_PATTERN;
                    packetLength = 1;
                  } else if (packetId[2] == 'R') {
                    pt = LEFT_LED_PATTERN;
                    packetLength = 1;
                  }
                  break;
                default:
                  break; 
              }
              break;
            case 'P':
              if (packetId[1] == 'L' && packetId[2] == 'S') {
                pt = PLAY_SONG;
                packetLength = 32;
              }
              break;
            case 'S':
              if (packetId[1] == 'T' && packetId[2] == 'S') {
                pt = STOP_SONG;
                packetLength = 0;
              }
              break;
            default:
              break;
          }
          Serial.printf("Packet ID %s, type %d\n", packetId, pt);
          if (pt == UNRECOGNIZED) {
            ps = WAITING;
          } else {
            ps = ID_RECEIVED;
            packetBufferIdx = 0;
          }
          if (packetBufferIdx >= packetLength) {
            packetReady = true;
            ps = WAITING;
            break;
          }
        }
      } else if (ps == ID_RECEIVED) {
        packetBuffer[packetBufferIdx++] = recv;
        if (packetBufferIdx >= packetLength) {
          packetReady = true;
          ps = WAITING;
          break;
        } 
        if (packetBufferIdx >= 32) {
          // invalid packet length
          ps = WAITING;
        }
      }
    }
    if (packetReady) {
      packetReady = false;
      led_command lcmd;
      audio_command acmd;
      acmd.song = songBuf;
      switch (pt) {
        case LEFT_LED_COLOR:
          lcmd.flags = 8;
          lcmd.red = packetBuffer[0];
          lcmd.green = packetBuffer[1];
          lcmd.blue = packetBuffer[2];
          xQueueSend(ledCmdQueue, &lcmd, 20);
          break;
        case RIGHT_LED_COLOR:
          lcmd.flags = 9;
          lcmd.red = packetBuffer[0];
          lcmd.green = packetBuffer[1];
          lcmd.blue = packetBuffer[2];
          xQueueSend(ledCmdQueue, &lcmd, 20);
          break;
        case LEFT_LED_DISABLE:
          lcmd.flags = 2;
          xQueueSend(ledCmdQueue, &lcmd, 20);
          break;
        case RIGHT_LED_DISABLE:
          lcmd.flags = 3;
          xQueueSend(ledCmdQueue, &lcmd, 20);
          break;  
        case LEFT_LED_ENABLE:
          lcmd.flags = 4;
          xQueueSend(ledCmdQueue, &lcmd, 20);
          break;  
        case RIGHT_LED_ENABLE:
          lcmd.flags = 5;
          xQueueSend(ledCmdQueue, &lcmd, 20);
          break;
        case PLAY_SONG:
          acmd.flags = 1;
          strcpy(songBuf, (const char *) packetBuffer);
          xQueueSend(audioCommandQueue, &acmd, 20);
          break;
        case STOP_SONG:
          acmd.flags = 2;
          xQueueSend(audioCommandQueue, &acmd, 20);
          break; 
        case DIR_LISTING:
          acmd.flags = 4;
          xQueueSend(audioCommandQueue, &acmd, 20);
          break;    
        case LEFT_LED_PATTERN:
          lcmd.flags = 16;
          lcmd.pattern = packetBuffer[0];
          xQueueSend(ledCmdQueue, &lcmd, 20);
          break;
        case RIGHT_LED_PATTERN:
          lcmd.flags = 17;
          lcmd.pattern = packetBuffer[0];
          xQueueSend(ledCmdQueue, &lcmd, 20);
          break;
        default:
          break;
      }
    }
    tx_packet toSend;
    int idx = 0;
    if (xQueueReceive(btSendQueue, &toSend, 0)) {
      while (idx < toSend.len) {
        bt.write(toSend.str[idx++]);
      }
    }
  }
}

union imu_telemetry_t {
  struct imu_acceleration_t {
    float x;
    float y;
    float z;  
  } floats;
  char bytes[3 * sizeof(float) + 1];
} imu_telemetry;

char imuBuf[17];

void sendImuTelemetry() {
  sprintf(imuBuf, "!IMU%s", imu_telemetry.bytes);
  char *ts = imuBuf;
  tx_packet pkt = (tx_packet) {
    .len = 16,
    .str = ts,
  };
  xQueueSend(btSendQueue, &pkt, 0);
}

char hallBuf[6];

void sendHallTelemetry(uint8_t ticks) {
  sprintf(hallBuf, "!HAL%c", ticks);
  Serial.printf("hallBuf = %s, ticks = %d\n", hallBuf, ticks);
  char *ts = hallBuf;
  tx_packet pkt = (tx_packet) {
    .len = 5,
    .str = ts,
  };
  xQueueSend(btSendQueue, &pkt, 0);
}

void imuThread(void *args) {
  LSM9DS1 imu;
  Wire.begin(21, 22);
  if (!imu.begin()) {
    Serial.println("IMU init failed");
    for (;;);
  }
  for (;;) {
//    if (imu.gyroAvailable()) {
//      imu.readGyro();
//      imu_values.gyroX = imu.calcGyro(imu.gx);
//      imu_values.gyroY = imu.calcGyro(imu.gy);
//      imu_values.gyroZ = imu.calcGyro(imu.gz);
//    }
    if (imu.accelAvailable()) {
      imu.readAccel(); 
      imu_telemetry.floats.x = imu.calcAccel(imu.ax);
      imu_telemetry.floats.y = imu.calcAccel(imu.ay);
      imu_telemetry.floats.z = imu.calcAccel(imu.az);
    } 
  }
}

#define NUM_LEDS 35
#define PATTERN_LENGTH 96

void writeLedUniform(CRGB *leds, uint8_t r, uint8_t g, uint8_t b) {
  for (uint8_t i = 0; i < NUM_LEDS; i++) {
    leds[i].r = r / 4;
    leds[i].g = g / 4;
    leds[i].b = b / 4;
  }
  FastLED.show();
}

// material ui colors
int colors[] = {
  0xcd100d, // red
  0x3f2600, // orange
  0x3f3a0e, // yellow
  0x33370e, // lime green
  0x132b14, // green
  0x002a3d, // light blue
  0x0f142d, // indigo
  0x2709b0, // purple
  0x3a072c, // pink
  0x3e3e3e, // white
};

void patternAdvance(CRGB *leds, int8_t pattern, uint8_t patternIndex) {
  for (uint8_t i = 0; i < NUM_LEDS; i++) {
    leds[i] = round(colors[pattern] * sinf(i + patternIndex * M_PI_4 / 3));
  }
  FastLED.show();
}

void ledThread(void *args) {
  CRGB leftLeds[NUM_LEDS];
  CRGB rightLeds[NUM_LEDS];
  FastLED.addLeds<NEOPIXEL, 27>(leftLeds, NUM_LEDS);
  FastLED.addLeds<NEOPIXEL, 23>(rightLeds, NUM_LEDS);
  FastLED.setMaxPowerInMilliWatts(5000);
  // initialize LEDs
  uint8_t leftR = 0, leftG = 0, leftB = 0, rightR = 0, rightG = 0, rightB = 0;
  int8_t rightPattern = -1;
  int8_t leftPattern = -1;
  uint8_t rightPatternIndex = 0;
  uint8_t leftPatternIndex = 0;
  writeLedUniform(rightLeds, 0, 0, 0);
  writeLedUniform(leftLeds, 0, 0, 0);
  for (;;) {
    led_command cmd;
    if (xQueueReceive(ledCmdQueue, &cmd, 0)) {
      if (cmd.flags & 1) {
        rightPattern = -1;
      } else {
        leftPattern = -1;
      }
      if (cmd.flags & 2) {
        // disable LED
        if (cmd.flags & 1) {
          writeLedUniform(rightLeds, 0, 0, 0);
        } else {
          writeLedUniform(leftLeds, 0, 0, 0);
        }
      } else if (cmd.flags & 4) {
        // enable LED
        if (cmd.flags & 1) {
          writeLedUniform(rightLeds, rightR, rightG, rightB); 
        } else {
          writeLedUniform(leftLeds, leftR, leftG, leftB);
        }
      } else if (cmd.flags & 8) {
        // update color
        if (cmd.flags & 1) {
          rightR = cmd.red;
          rightG = cmd.green;
          rightB = cmd.blue;
          writeLedUniform(rightLeds, rightR, rightG, rightB); 
        } else {
          leftR = cmd.red;
          leftG = cmd.green;
          leftB = cmd.blue;
          writeLedUniform(leftLeds, leftR, leftG, leftB);
        }
      } else if (cmd.flags & 16) {
        // pattern
        if (cmd.flags & 1) {
          rightPattern = cmd.pattern;
        } else {
          leftPattern = cmd.pattern;
        }
      }
    }
    if (leftPattern >= 0 || rightPattern >= 0) {
      if (rightPattern >= 0) {
        patternAdvance(rightLeds, rightPattern, rightPatternIndex++);
        if (rightPatternIndex >= PATTERN_LENGTH) rightPatternIndex = 0;
      }
      if (leftPattern >= 0) {
        patternAdvance(leftLeds, leftPattern, leftPatternIndex++);
        if (leftPatternIndex >= PATTERN_LENGTH) leftPatternIndex = 0;
      }
      delay(50);
    }
  }
}

void audioThread(void *args) {
  // initialize audio
  for (;;) {
    audio_command cmd;
    if (xQueueReceive(audioCommandQueue, &cmd, 0)) {
      if (cmd.flags & 1) {
        // start playing
      } else if (cmd.flags & 2) {
        // stop playing
      } else if (cmd.flags & 4) {
        // list songs
        // for each file:
//          char songNameBuf[37];
//          sprintf(songNameBuf, "!AUD%s", file.name());
//          char *sn = songNameBuf;
//          tx_packet pkt = (tx_packet) {
//            .len = 36,
//            .str = sn,
//          };
//          xQueueSend(btSendQueue, &pkt, 40);
      }
    }
  }
}

volatile uint8_t hallTickCounter = 0;

//typedef enum {
//  START,
//  LEVEL_HIGH,
//  FALLEN,
//  LEVEL_LOW,
//  RISEN,
//} hall_state;
//
//volatile hall_state state = START;
//  
//void IRAM_ATTR hallISRRising() {
//  Serial.println("Rising edge");
//  state = RISEN;
//}
//
void hallISRFalling() {
  hallTickCounter++;
}
//
//volatile uint8_t hallTicks = 0;
//int inum = 32;
//
//void hallThread(void *args) {
//  while (true) {
//    switch (state) {
//      case START:
//        attachInterrupt(inum, hallISRFalling, FALLING);
//        state = LEVEL_HIGH;
//        break;
//      case RISEN:
//        detachInterrupt(inum);
//        attachInterrupt(inum, hallISRFalling, FALLING);
//        state = LEVEL_HIGH;
//        hallTicks++;
//        break;
//      case FALLEN:
//        detachInterrupt(inum);
//        attachInterrupt(inum, hallISRRising, RISING);
//        state = LEVEL_LOW;
//        break;
//      default:
//        break;
//    }
//  }
//}

void setup() {
  Serial.begin(115200);
  btSendQueue = xQueueCreate(12, sizeof(tx_packet));
  ledCmdQueue = xQueueCreate(8, sizeof(led_command));
  audioCommandQueue = xQueueCreate(8, sizeof(audio_command));
  TaskHandle_t bluetoothHandle = NULL;
  xTaskCreate(&bluetoothThread, "BT", STACK_DEPTH, NULL, tskIDLE_PRIORITY, &bluetoothHandle);
  TaskHandle_t imuHandle = NULL;
  xTaskCreate(&imuThread, "IMU", STACK_DEPTH, NULL, tskIDLE_PRIORITY, &imuHandle);
  TaskHandle_t ledHandle = NULL;
  xTaskCreate(&ledThread, "LED", STACK_DEPTH, NULL, tskIDLE_PRIORITY, &ledHandle);
  TaskHandle_t audioHandle = NULL;
  xTaskCreate(&audioThread, "AUDIO", STACK_DEPTH, NULL, tskIDLE_PRIORITY, &audioHandle);
//  TaskHandle_t hallHandle = NULL;
//  xTaskCreate(&hallThread, "HALL", STACK_DEPTH, NULL, tskIDLE_PRIORITY, &hallHandle);
  pinMode(3, INPUT);
  attachInterrupt(digitalPinToInterrupt(3), hallISRFalling, FALLING);
}
void loop() {
  sendImuTelemetry();
  delay(500);
  sendHallTelemetry(hallTickCounter);
  hallTickCounter = 0;
  delay(500);
} 

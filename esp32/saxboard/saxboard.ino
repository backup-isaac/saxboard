#include "BluetoothSerial.h"
#include "freertos/queue.h"
#include "freertos/task.h"
#include <Wire.h>
#include <SPI.h>
#include <SparkFunLSM9DS1.h>
#include <stdint.h>
#include <stdbool.h>
#include <string.h>

#if !defined(CONFIG_BT_ENABLED) || !defined(CONFIG_BLUEDROID_ENABLED)
#error Bluetooth is not enabled! Please run `make menuconfig` to and enable it
#endif

#define STACK_DEPTH 4096 
#define BUF_SIZE 64

//SemaphoreHandle_t btRecvSemaphore = NULL;
uint8_t btReceiveBuf[BUF_SIZE];
//bool btAvailable = false;

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
  VOLUME,
  DIR_LISTING,
} packet_type;

typedef struct {
  uint8_t flags; // bit 0 = right led (1) or left led (0); bit 1 set -> disable command; bit 2 set -> enable command; bit 3 set -> color command
  uint8_t red; // only inspect the colors if bit 3 set
  uint8_t green;
  uint8_t blue;
} led_command;

typedef struct {
  uint8_t flags; // bit 0 set -> play command; bit 1 set -> stop command; bit 2 set -> volume command; bit 3 set -> directory list command
  uint8_t volume; // only inspect volume if bit 2 set
  char *song; // only inspect song if bit 0 set
} audio_command;

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
//    if (bt.available() && !btAvailable) {
//      uint8_t index = 0;
//      if (xSemaphoreTake(btRecvSemaphore, 10)) {
//        while (bt.available() && index < BUF_SIZE - 1) {
//          btReceiveBuf[index++] = bt.read();
//        }
//        btReceiveBuf[index] = 0;
//        btAvailable = true;
//        xSemaphoreGive(btRecvSemaphore);
//      }
//    }
    while (bt.available()) {
      uint8_t recv = bt.read();
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
            case 'V':
              if (packetId[1] == 'O' && packetId[2] == 'L') {
                pt = VOLUME;
                packetLength = 1;
              }
              break;
            default:
              break;
          }
          if (pt == UNRECOGNIZED) {
            ps = WAITING;
          } else {
            ps = ID_RECEIVED;
            packetBufferIdx = 0;
          }
        }
      } else if (ps == ID_RECEIVED) {
        if (packetBufferIdx >= packetLength) {
          packetReady = true;
          ps = WAITING;
          break;
        } else if (packetBufferIdx >= 32) {
          // invalid packet length
          ps = WAITING;
        }
        packetBuffer[packetBufferIdx++] = recv;
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
        case VOLUME:
          acmd.flags = 4;
          acmd.volume = packetBuffer[0];
          xQueueSend(audioCommandQueue, &acmd, 20);
          break; 
        case DIR_LISTING:
          acmd.flags = 8;
          xQueueSend(audioCommandQueue, &acmd, 20);
          break;    
        default:
          break;
      }
    }
    char *toSend;
    if (xQueueReceive(btSendQueue, &toSend, 10)) {
      while (*toSend) {
        bt.write(*toSend);
        toSend++;
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
  xQueueSend(btSendQueue, &ts, 0);
}

char hallBuf[6];

void sendHallTelemetry(uint8_t ticks) {
  sprintf(hallBuf, "!HAL%s", &ticks);
  char *ts = hallBuf;
  xQueueSend(btSendQueue, &ts, 0);
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

void ledThread(void *args) {
  // initialize LEDs
  for (;;) {
    led_command cmd;
    if (xQueueReceive(ledCmdQueue, &cmd, 20)) {
      if (cmd.flags & 2) {
        // disable LED
      } else if (cmd.flags & 4) {
        // enable LED
      } else if (cmd.flags & 8) {
        // update color
      }
    }
  }
}

void audioThread(void *args) {
  // initialize audio
  for (;;) {
    audio_command cmd;
    if (xQueueReceive(audioCommandQueue, &cmd, 20)) {
      if (cmd.flags & 1) {
        // start playing
      } else if (cmd.flags & 2) {
        // stop playing
      } else if (cmd.flags & 4) {
        // change volume
      } else if (cmd.flags & 8) {
        // list songs
        // for each song on the sd card:
        // char songNameBuf[37];
        // sprintf(songNameBuf, "!AUD%s", <name of song>);
        // char *sn = songNameBuf;
        // xQueueSend(btSendQueue, &sn, 40);
      }
    }
  }
}

void setup() {
  Serial.begin(115200);
//  btRecvSemaphore = xSemaphoreCreateBinary(); 
//  xSemaphoreGive(btRecvSemaphore);
  btSendQueue = xQueueCreate(12, sizeof(char *));
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
}

uint32_t i = 0;

void loop() {
//  if (btAvailable) {
//    xSemaphoreTake(btRecvSemaphore, portMAX_DELAY);
//    Serial.printf("Bluetooth received: %s\n", btReceiveBuf);
//    btAvailable = false;
//    xSemaphoreGive(btRecvSemaphore);
//  }
  sendImuTelemetry();
  if (i % 4 == 0) {
    // TODO replace 0 with tick counter
    sendHallTelemetry(0);
  }
  delay(1000);
} 

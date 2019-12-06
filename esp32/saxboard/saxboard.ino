#include "BluetoothSerial.h"
#include "freertos/queue.h"
#include "freertos/semphr.h"
#include "freertos/task.h"
#include <Wire.h>
#include <SPI.h>
#include <SparkFunLSM9DS1.h>
#include <stdint.h>
#include <stdbool.h>

#if !defined(CONFIG_BT_ENABLED) || !defined(CONFIG_BLUEDROID_ENABLED)
#error Bluetooth is not enabled! Please run `make menuconfig` to and enable it
#endif

#define STACK_DEPTH 4096 
#define BUF_SIZE 64

SemaphoreHandle_t btRecvSemaphore = NULL;
uint8_t btReceiveBuf[BUF_SIZE];
bool btAvailable = false;

QueueHandle_t btSendQueue;

void bluetoothThread(void *args) {
  BluetoothSerial bt;
  bt.begin("ESP32test");
  Serial.println("The device started, now you can pair it with bluetooth!");
  for (;;) {
    if (bt.available() && !btAvailable) {
      uint8_t index = 0;
      if (xSemaphoreTake(btRecvSemaphore, 10)) {
        while (bt.available() && index < BUF_SIZE - 1) {
          btReceiveBuf[index++] = bt.read();
        }
        btReceiveBuf[index] = 0;
        btAvailable = true;
        xSemaphoreGive(btRecvSemaphore);
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

void setup() {
  Serial.begin(115200);
  btRecvSemaphore = xSemaphoreCreateBinary(); 
  xSemaphoreGive(btRecvSemaphore);
  btSendQueue = xQueueCreate(8, sizeof(char *));
  TaskHandle_t bluetoothHandle = NULL;
  xTaskCreate(&bluetoothThread, "BT", STACK_DEPTH, NULL, tskIDLE_PRIORITY, &bluetoothHandle);
  TaskHandle_t imuHandle = NULL;
  xTaskCreate(&imuThread, "IMU", STACK_DEPTH, NULL, tskIDLE_PRIORITY, &imuHandle);
}

void loop() {
  if (btAvailable) {
    xSemaphoreTake(btRecvSemaphore, portMAX_DELAY);
    Serial.printf("Bluetooth received: %s\n", btReceiveBuf);
    btAvailable = false;
    xSemaphoreGive(btRecvSemaphore);
  }
  sendImuTelemetry();
  delay(250);
} 

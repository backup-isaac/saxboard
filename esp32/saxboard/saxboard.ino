#include "BluetoothSerial.h"
#include "freertos/semphr.h"
#include "freertos/task.h"
#include <stdint.h>
#include <stdbool.h>

#if !defined(CONFIG_BT_ENABLED) || !defined(CONFIG_BLUEDROID_ENABLED)
#error Bluetooth is not enabled! Please run `make menuconfig` to and enable it
#endif

#define STACK_DEPTH 16384 
#define BUF_SIZE 64

SemaphoreHandle_t btSemaphore = NULL;
uint8_t btReceiveBuf[BUF_SIZE];
bool btAvailable = false;

void bluetoothReceiveThread(void *args) {
  BluetoothSerial bt;
  bt.begin("ESP32test");
  Serial.println("The device started, now you can pair it with bluetooth!");
  for (;;) {
    if (bt.available() && !btAvailable) {
      uint8_t index = 0;
      xSemaphoreTake(btSemaphore, portMAX_DELAY);
      while (bt.available() && index < BUF_SIZE - 1) {
        btReceiveBuf[index++] = bt.read();
      }
      btReceiveBuf[index] = 0;
      btAvailable = true;
      xSemaphoreGive(btSemaphore);
    }
  }
}

void setup() {
  Serial.begin(115200);
  btSemaphore = xSemaphoreCreateBinary(); 
  xSemaphoreGive(btSemaphore);
  TaskHandle_t bluetoothHandle = NULL;
  xTaskCreate(&bluetoothReceiveThread, "BTRECV", STACK_DEPTH, NULL, tskIDLE_PRIORITY, &bluetoothHandle);
}

void loop() {
  if (btAvailable) {
    xSemaphoreTake(btSemaphore, portMAX_DELAY);
    Serial.printf("Bluetooth: %s\n", btReceiveBuf);
    btAvailable = false;
    xSemaphoreGive(btSemaphore);
  }
  delay(1);
} 

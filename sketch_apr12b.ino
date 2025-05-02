#include "MPU9250.h"

MPU9250 mpu;

float AngleX = 0;
float AngleY = 0;
float AngleZ = 0;
int i = 0;

void setup() {
    Serial.begin(115200);
    Wire.begin(21, 5);
    while (!Serial)
      delay(10); // will pause Zero, Leonardo, etc until serial console opens

    mpu.verbose(true);
    if (!mpu.setup(0x68)) {  // change to your own address
        while (1) {
            Serial.println("MPU connection failed. Please check your connection with `connection_check` example.");
            delay(5000);
        }
    }
    
    
    Serial.print("Accel Gyro calibration will start in 5sec.");//注意将模块放平稳
    delay(5000); 
    mpu.calibrateAccelGyro();
    Serial.println(" ");
    Serial.print("Mag calibration will start in 5sec.");//注意将模块按8字旋转
    delay(5000);
    mpu.calibrateMag();
    mpu.verbose(false);
}

void loop() {
    mpu.update();
    
    static uint32_t prev_ms = millis();
    
    if (millis() > prev_ms + 25) 
    {
      
        if(mpu.getRoll()>=0)AngleX = 360-mpu.getRoll();
        else AngleX = -mpu.getRoll();
        Serial.print("@SET 100,");Serial.println(AngleX);
        
        if(mpu.getPitch()>=0)AngleY = mpu.getPitch();
        else AngleY = 360-(-mpu.getPitch());
        Serial.print("@SET 101,");Serial.println(AngleY);
        
        if(mpu.getYaw()>=0)AngleZ = mpu.getYaw();
        else AngleZ = 360-(-mpu.getYaw());
        Serial.print("@SET 102,");Serial.println(AngleZ); 
      
        Serial.print("@SET 103,");Serial.println(-mpu.getRoll());
        Serial.print("@SET 104,");Serial.println(mpu.getPitch());
        Serial.print("@SET 105,");Serial.println(mpu.getYaw());
        
        prev_ms = millis();
        i++;
    }
    if(i==10)
    {
      i=0;
      Serial.println(" ");
      Serial.print("AccX:"); Serial.print(mpu.getAccX()); Serial.print(" ");
      Serial.print("AccY:"); Serial.print(mpu.getAccY()); Serial.print(" ");
      Serial.print("AccZ:"); Serial.print(mpu.getAccZ()); Serial.print(" ");
      Serial.print("GyroX:"); Serial.print(mpu.getGyroX()); Serial.print(" ");
      Serial.print("GyroY:"); Serial.print(mpu.getGyroY()); Serial.print(" ");
      Serial.print("GyroZ:"); Serial.print(mpu.getGyroZ()); Serial.print(" ");
      Serial.print("MagX:"); Serial.print(mpu.getMagX()); Serial.print(" ");
      Serial.print("MagY:"); Serial.print(mpu.getMagY()); Serial.print(" ");
      Serial.print("MagZ:"); Serial.print(mpu.getMagZ()); Serial.print("");
    }
}

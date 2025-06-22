#include <Wire.h>
#include <string.h>
#include <MPU6050_tockn.h>
#include <BluetoothSerial.h>
/*-------------------------------------------------
  测试马达转动速度；
  通过串口输入'1'PWMX加1，输入'2'PWMX减1。
  模块AIN1、AIN2控制电机A，BIN1、BIN2控制电机B。
  测试结果 12v电压供给下，  PWMX+为前进，PWMX-为后退
  左马达A， PWM 9起转  右马达B，PWM 12起转
  -------------------------------------------------
*/

/*-------设置ESP32引脚号-------*/
#define SDA_Pin 21         //定义陀螺仪SDA针脚库默认
#define SCL_Pin 5         //定义陀螺仪SCL针脚库默认
#define Left_PWMA 13      //定义左轮TB6612引脚PWMA
#define Left_AIN1 14      //定义左轮TB6612引脚A1
#define Left_AIN2 12      //定义左轮TB6612引脚A2
#define Left_EnCoderA 32  //定义左轮编码器A相引脚
#define Left_EnCoderB 19  //定义左轮编码器B相引脚
#define Right_PWMB 33  //定义右轮TB6612引脚PWMB
#define Right_BIN1 25  //定义右轮TB6612引脚B1
#define Right_BIN2 26  //定义右轮TB6612引脚B2
#define Right_EnCoderA 18        //定义右轮编码器A相引脚
#define Right_EnCoderB 35  //定义右轮编码器B相引脚
#define Stby_Pin 27        //定义TB6612引脚STBY
#define LStar_PWM 4       //测试出左电机起转PWM补偿值
#define RStar_PWM 4       //测试出右电机起转PWM补偿值
#define TIMEX 100          //定义时间间隔

/*-------蓝牙相关配置-------*/
#define BT_DISCOVER_TIME	10000
esp_spp_sec_t sec_mask=ESP_SPP_SEC_NONE; // or ESP_SPP_SEC_ENCRYPT|ESP_SPP_SEC_AUTHENTICATE to request pincode confirmation
esp_spp_role_t role=ESP_SPP_ROLE_SLAVE; // or ESP_SPP_ROLE_MASTER
//Found a device asynchronously: Name: 小米蓝牙手柄, Address: 1c:96:5a:f4:f8:c1, cod: 9480, rssi: -30

// 创建蓝牙串口对象
BluetoothSerial SerialBT;

// 小米手柄设备信息
String xiaomiControllerName = "小米蓝牙手柄";
String xiaomiControllerAddress = "1c:96:5a:f4:f8:c1";
bool xiaomiControllerFound = false;
BTAdvertisedDevice* xiaomiControllerDevice = nullptr;

// 手柄控制状态
bool controllerConnected = false;
int controllerSpeed = 0;    // 速度控制 (-100 到 100)
int controllerTurn = 0;     // 转向控制 (-50 到 50)
bool controllerEnabled = false;  // 是否启用手柄控制

MPU6050 Mpu6050(Wire);

/*-------定义调试和预设变量-------*/
float ANG_Kp = 5.5, ANG_Ki = 0.5, ANG_Kd = 0.5;  //反复调试得到角度环的Kp Ki Kd的值
float SPD_Kp = 0, SPD_Ki = SPD_Kp / 200;           //反复调试得到速度环的Kp Ki Kd的值  通常SPD_Ki = SPD_Kp / 200
float Turn_Kp = 0, Turn_SPD;                      //转向环Kp值通常为0.6 

/*-------定义平衡车控制变量-------*/
float Keep_Angle = -2.6, ANG_DIF_Val, ANG_INTG_Val;  //平衡车需要保持的角度，存在的角度偏差，偏差积分变量
float AngleX, AngleY, GyroX;                         //Mpu6050输出的角度值为浮点数，两位有效小数
float Spd_Last;                                      //上一次的速度数值
float ANG_PWM, SPD_PWM, Turn_PWM, TOT_PWM;           //定义角度PWM、速度PWM、转向PWM和合计PWM
long SPD_INTG_ValA, SPD_INTG_ValB;                   //定义速度积分变量
int SPD_A = 0, SPD_B = 0;                            //定义速度脉冲数
unsigned long Last_Time;                            //定义角度PWM

// 定义缓冲区大小
#define BUFFER_SIZE 128
char buffer[BUFFER_SIZE];

/*-------定义角度环PID调试程序输出电机电压PWM数值-------*/
/* 用陀螺仪返回的数据计算直立PID的PWM
   前倾陀螺仪X轴为正，后仰陀螺仪X轴为负
   车子前后移动保持平衡状态
*/
void AnglePID_PWMCount() {  //计算电机转动需要的PWM数值
  Mpu6050.update();  //刷新陀螺仪数据
  AngleX = Mpu6050.getAngleX(); //获取陀螺仪X方向角度
  AngleY = Mpu6050.getAngleY(); //获取陀螺仪Y方向角度
  GyroX = Mpu6050.getGyroX();  //获取陀螺仪X方向角加速度
  if (abs(AngleX) <= 50) {
    ANG_DIF_Val = AngleX - Keep_Angle;  //计算小车偏转角度与静态平衡角度的差值。
    ANG_INTG_Val += ANG_DIF_Val; //计算角度偏差的积分，INTG_Val为全局变量，一直积累
    ANG_INTG_Val = constrain(ANG_INTG_Val, -2000, 2000);//误差积分的最大和最小值
    ANG_PWM = ANG_Kp * ANG_DIF_Val + ANG_Ki * ANG_INTG_Val + ANG_Kd * GyroX;
  } else ANG_PWM = 0;

/*
  Serial.print("AngleX:");
  Serial.print(AngleX);
  Serial.print(',');
  Serial.print("AngleY:");
  Serial.print(AngleY);
  Serial.print(',');
  Serial.print("GyroX:");
  Serial.print(GyroX);
  Serial.print(',');
  Serial.print("ANG_DIF_Val:");
  Serial.print(ANG_DIF_Val);
  Serial.print(',');
  Serial.print("ANG_INTG_Val:");
  Serial.print(ANG_INTG_Val);
  Serial.print(',');
  Serial.print("SPD_A:");
  Serial.print(SPD_A);
  Serial.print(',');  //打印出左侧A电机速度脉冲值
  Serial.print("SPD_B:");
  Serial.print(SPD_B);
  Serial.println(',');  //打印出右侧B电机速度脉冲值
  Serial.print("直立环:");
  Serial.println(ANG_PWM);
  */
}

/*-------定义速度环PID调试程序输出电机电压PWM数值-------*/
/* 通过电机转动算出速度环PID的PWM
   前进左轮A速度环为正，右纶B速度环为负
   用于辅助小车尽快平衡
*/
void SpeedPID_PWMCount(int Spd_LA, int Spd_RB) {  //车轮位移PID计算PWM，通过电机编码器返回数据计算
  float Spd_Val;
  long SPD_INTG_Val;
  Spd_Val = (Spd_LA + Spd_RB) / 2;           //消除两轮不一致的误差
  Spd_Val = 0.3 * Spd_Val + 0.7 * Spd_Last;  //★增加速度环一阶滤波器，让数值平缓过渡
  Spd_Last = Spd_Val;
  SPD_INTG_Val += Spd_Val;
  SPD_INTG_Val = constrain(SPD_INTG_Val, -2000, 2000);
  if ((abs(AngleX) <= 9) && (abs(AngleY) < 5)) {
    SPD_PWM = SPD_Kp * Spd_Val + SPD_Ki * SPD_INTG_Val;  //通过调节PI计算速度环PWM数值
  } else SPD_PWM = 0;
  if (ANG_PWM < 0) SPD_PWM = -SPD_PWM;
}

/*-------定义转向环PID调试程序输出电机电压PWM数值-------*/
void TurnPID_PWMCount() {  //转向PMW计算
  Turn_PWM = Turn_Kp * Turn_SPD;
}

/*-------定义马达初始化函数-------*/
void Motor_begin() {
  /*-------定义A马达驱动引脚-------*/
  pinMode(Left_PWMA, OUTPUT);
  pinMode(Left_AIN1, OUTPUT);
  pinMode(Left_AIN2, OUTPUT);
  pinMode(Left_EnCoderA, INPUT);
  pinMode(Left_EnCoderB, INPUT);
  /*-------定义B马达驱动引脚-------*/
  pinMode(Right_PWMB, OUTPUT);
  pinMode(Right_BIN1, OUTPUT);
  pinMode(Right_BIN2, OUTPUT);
  pinMode(Stby_Pin, OUTPUT);
  pinMode(Right_EnCoderA, INPUT);
  pinMode(Right_EnCoderB, INPUT);
}

/*-------定义陀螺仪初始化程序-------*/
void MPU6050_begin() {
  Wire.begin(SDA_Pin, SCL_Pin);
  Mpu6050.begin();
  Mpu6050.calcGyroOffsets(true);
  //Mpu6050.setGyroOffsets(-1.67, 0.36, -1.92);  //后期可直接设置不用每次都测试
} 

/*-------搜索蓝牙-------*/
void btAdvertisedDeviceFound(BTAdvertisedDevice* pDevice) {
	Serial.printf("Found a device asynchronously: %s\n", pDevice->toString().c_str());
	
	// 检查是否是小米手柄
	if (pDevice->getName() == xiaomiControllerName.c_str() || 
	    pDevice->getAddress().toString() == xiaomiControllerAddress.c_str()) {
		Serial.println("找到小米手柄设备！");
		xiaomiControllerFound = true;
		xiaomiControllerDevice = pDevice;
		Serial.printf("设备名称: %s\n", pDevice->getName().c_str());
		Serial.printf("设备地址: %s\n", pDevice->getAddress().toString().c_str());
		Serial.printf("信号强度: %d\n", pDevice->getRSSI());
	}
}

void setup() {
  Serial.begin(115200);
  delay(1000);
  
  Motor_begin();    //电机初始化
  MPU6050_begin();  //陀螺仪初始化
  attachInterrupt(Left_EnCoderB, EnCoder_CountA, CHANGE);
  attachInterrupt(Right_EnCoderB, EnCoder_CountB, CHANGE);
  pinMode(2, OUTPUT);
  initBluetooth();  // 初始化蓝牙

  Last_Time = millis();
}

void loop() {
  // Serial.println("=======loop======");
  GetCommand();
  handleXiaomiControllerData();  // 处理小米手柄数据
  checkBluetoothConnection();    // 检查蓝牙连接状态
  AnglePID_PWMCount();  //角度环PWM计算
  // if (SPD_A >= 10)
  //   SpeedPID_PWMCount(SPD_A, SPD_B);  //当速度大于10时开启速度环PWM计算
  // TurnPID_PWMCount();                 //转向环PWM计算
  // TOT_PWM = ANG_PWM - SPD_PWM;        //串联角度环和速度环
  // Car_DRV(TOT_PWM);
  Car_DRV(ANG_PWM);
//  INT_TIMER();  //定时中断程
}

/*-------定义编码器AB相计数程序-------*/
void EnCoder_CountA() {
  if (digitalRead(Left_EnCoderA) == HIGH)
    if (digitalRead(Left_EnCoderB) == LOW)
      SPD_A++;
    else SPD_A--;
  else {
    if (digitalRead(Left_EnCoderB) == HIGH)
      SPD_A++;
    else SPD_A--;
  }
}

void EnCoder_CountB() {
  if (digitalRead(Right_EnCoderA) == HIGH)
    if (digitalRead(Right_EnCoderB) == LOW)
      SPD_B++;
    else SPD_B--;
  else {
    if (digitalRead(Right_EnCoderB) == HIGH)
      SPD_B++;
    else SPD_B--;
  }
}

/*-------★可视化PWM曲线★-------*/
void View_PWM() {
  char buffer[200];  // 用于存储格式化后的字符串
  sprintf(buffer, "AngleX=%.2f, AngleY=%.2f, GyroX=%.2f,\nANG_Kp=%.2f, ANG_Ki=%.2f, ANG_Kd=%.2f, ANG_INTG_Val=%.2f \nANG_PWM=%.2f", AngleX, AngleY, GyroX, ANG_Kp, ANG_Ki, ANG_Kd, ANG_INTG_Val, ANG_PWM);
  sendCommandByBuffer(101, buffer);
}

/*-------定义中断程序-------*/
void INT_TIMER() {
  if ((millis() - Last_Time) >= TIMEX) {
    View_PWM();                            //绘制PWM波形图
    SPD_A = SPD_B = 0;     //计数器清零重新计数
    Last_Time = millis();  //设置上一次时间为当前时间
  }
}

/*-------定义小车运动函数-------*/
void Car_DRV(int Pwm) {                  //两个电机转动方向镜像对称，小车向一个方向行驶
  /*-------加上两轮起转PWM数值-------*/  //需要提前测试出电机起转PWM值
  int Left_PWM, Right_PWM;
  
  // 如果手柄已连接且启用，则使用手柄控制
  if (controllerConnected && controllerEnabled) {
    // 将手柄控制值转换为PWM
    int basePWM = map(abs(controllerSpeed), 0, 100, 0, 200);  // 基础PWM
    if (controllerSpeed < 0) basePWM = -basePWM;
    
    Left_PWM = basePWM + controllerTurn;
    Right_PWM = basePWM - controllerTurn;
    
    // 限制PWM范围
    Left_PWM = constrain(Left_PWM, -255, 255);
    Right_PWM = constrain(Right_PWM, -255, 255);
  } else {
    // 使用原有的平衡控制逻辑
    if (Pwm > 0) {
      Left_PWM = Pwm + LStar_PWM;   //加上左马达电压补偿PWM值
      Right_PWM = Pwm + RStar_PWM;  //加上右马达电压补偿PWM值
    }
    if (Pwm < 0) {
      Left_PWM = Pwm - LStar_PWM;
      Right_PWM = Pwm - RStar_PWM;
    }
    if (Pwm == 0) {
      Left_PWM = 0;
      Right_PWM = 0;
    }

    /*-------控制转向-------*/
    if (Turn_PWM > 0) Left_PWM += Turn_PWM;   //左转左轮多运动
    if (Turn_PWM < 0) Right_PWM -= Turn_PWM;  //右转右轮多运动
  }

  /*-------控制电机转动-------*/
  Motor_DRV(Left_AIN1, Left_AIN2, Left_PWMA, Left_PWM);       //输入左侧电机3个针脚和PWM数值
  Motor_DRV(Right_BIN1, Right_BIN2, Right_PWMB, -Right_PWM);  //输入右侧电机3个针脚和PWM数值
}

/*-------定义电机转动程序-------*/
void Motor_DRV(int Pin1, int Pin2, int PinPwm, int PwmVal) {
  PwmVal = constrain(PwmVal, -255, 255);  //限定Pwm区间在-255~255
  digitalWrite(Stby_Pin, HIGH);
  analogWrite(PinPwm, abs(PwmVal));  //设置输出的PWM数值
  if (PwmVal == 0) {                 //如果PWM为0则输出针脚为低电压，马达停止转动
    digitalWrite(Pin1, LOW);
    digitalWrite(Pin2, LOW);
  }
  if (PwmVal > 0) {  //如果PWM大于0则电机顺时针转动
    digitalWrite(Pin1, HIGH);
    digitalWrite(Pin2, LOW);
  }
  if (PwmVal < 0) {  //如果PWM小于0则电机逆时针转动
    digitalWrite(Pin1, LOW);
    digitalWrite(Pin2, HIGH);
  }
}

int stringToInt(String str) {
  int sign = 1;
  int num = 0;
  int i = 0;

  if (str.charAt(0) == '-') {
    sign = -1;
    i = 1;
  }

  for (; i < str.length(); i++) {
    if (str.charAt(i) >= '0' && str.charAt(i) <= '9') {
      num = num * 10 + (str.charAt(i) - '0');
    } else {
      return 0;
    }
  }

  return num * sign;
}

void splitString(const char* str, String result[], int& resultSize) {
  int wordIndex = 0;
  char* token;
  char strCopy[strlen(str) + 1];
  strcpy(strCopy, str);

  token = strtok(strCopy, " ");
  while (token != NULL) {
    result[wordIndex++] = String(token);
    token = strtok(NULL, " ");
  }
  resultSize = wordIndex;
}

void GetCommand() {
  if (SerialBT.available()) {
    // 使用更高效的读取方式
    int len = SerialBT.readBytesUntil('\n', buffer, BUFFER_SIZE - 1);
    if (len > 0) {
      buffer[len] = '\0';  // 确保字符串结束
      
      // 快速解析命令
      char* cmd = strtok(buffer, " ");
      if (cmd != NULL) {
        char command = cmd[0];
        int args[10];
        int argsCount = 0;
        
        // 解析参数
        char* arg = strtok(NULL, " ");
        while (arg != NULL && argsCount < 10) {
          args[argsCount++] = atoi(arg);
          arg = strtok(NULL, " ");
        }
        
        // 执行命令
        onCommand(command, args, argsCount);
        Blink();
      }
    }
  }
}

void sendCommandByBuffer(int command, char* args) {
  if (SerialBT.connected()) {
    // 使用更高效的格式化方式
    int len = snprintf(buffer, BUFFER_SIZE, "%d %s\n", command, args);
    if (len > 0 && len < BUFFER_SIZE) {
      SerialBT.write((uint8_t*)buffer, len);
    }
  }
}

void sendCommand(int command, String args) {
  if (SerialBT.connected()) {
    int len = snprintf(buffer, BUFFER_SIZE, "%d %s\n", command, args.c_str());
    if (len > 0 && len < BUFFER_SIZE) {
      SerialBT.write((uint8_t*)buffer, len);
    }
  }
}

void setPID(int Kp, int Ki, int Kd, int KAngel) {
  Serial.println("===setPID====");
  ANG_Kp = Kp/100.0f;
  ANG_Ki = Ki/100.0f;
  ANG_Kd = Kd/100.0f;
  Keep_Angle = KAngel/100.0f;
}

void move(int arg1, int arg2) {
  Serial.println("===move====");
  Serial.println(arg1);
  Serial.println(arg2);
}

void onCommand(char command, int* args, int argsCount) {
  Serial.print("===onCommand====");
  Serial.print(command);
  Serial.print(" ");

  for (int i = 0; i < argsCount; i++) {
    Serial.print(args[i]);
    Serial.print(" ");
  }

  Serial.println("");

  switch (command) {
    case '0': sendCommand(100, "test ok"); break;  //手机连接发送连接信号，发送100表示成功
    case '1': move(args[0], args[1]); break;
    case '3': setPID(args[0], args[1], args[2], args[3]); break;
    default:
      break;
      // case '1': displayPIDMsg(101); break; //向手机发送101，并带上PID的信息
      // case '2': setPIDMsg(); break; //调节PID值
      // case '3': displayCarStatus(103); break; //车速度、倾斜角度等
      // case '4': moveFront(); break; //前进
      // case '5': moveBack(); break; //后退
      // case '6': moveLeft(); break; //左转
      // case '7': moveRight(); break; //右转
      // case '8': keepBalance(); break; //前进
  }
}

/*-------连接小米手柄-------*/
bool connectToXiaomiController() {
	if (!xiaomiControllerFound || xiaomiControllerDevice == nullptr) {
		Serial.println("未找到小米手柄设备，无法连接");
		return false;
	}
	
	Serial.println("尝试连接小米手柄...");
	Serial.printf("连接地址: %s\n", xiaomiControllerDevice->getAddress().toString().c_str());

  BTAddress addr;
  int channel=0;

  BTAdvertisedDevice *device = xiaomiControllerDevice;
  Serial.printf(" ----- %s  %s %d\n", device->getAddress().toString().c_str(), device->getName().c_str(), device->getRSSI());
  std::map<int,std::string> channels=SerialBT.getChannels(device->getAddress());
  Serial.printf("scanned for services, found %d\n", channels.size());
  for(auto const &entry : channels) {
    Serial.printf("     channel %d (%s)\n", entry.first, entry.second.c_str());
  }

  addr = device->getAddress();
  if(channels.size() > 0) {
    channel=channels.begin()->first;
  }
  
  if(addr) {
    Serial.printf("connecting to %s - %d\n", addr.toString().c_str(), channel);
    
    // 尝试连接到小米手柄
    if (SerialBT.connect(addr, channel, sec_mask, role)) {
      Serial.println("成功连接到小米手柄！");
      controllerConnected = true;
      controllerEnabled = true;
      return true;
    } else {
      Serial.println("连接小米手柄失败");
      controllerConnected = false;
      return false;
    }
  }

  return false;
}

/*-------处理小米手柄数据-------*/
void handleXiaomiControllerData() {
  if (SerialBT.available()) {
    // 读取手柄数据
    uint8_t data[64];
    int len = SerialBT.readBytes(data, sizeof(data));
    
    if (len > 0) {
      Serial.print("收到手柄数据，长度: ");
      Serial.println(len);
      
      // 打印原始数据用于调试
      Serial.print("原始数据: ");
      for (int i = 0; i < len; i++) {
        Serial.printf("%02X ", data[i]);
      }
      Serial.println();
      
      // 解析手柄数据（这里需要根据小米手柄的具体协议来解析）
      // 通常手柄数据包含摇杆、按钮等信息
      if (len >= 8) {
        // 假设前8字节包含基本控制信息
        uint8_t leftStickX = data[0];
        uint8_t leftStickY = data[1];
        uint8_t rightStickX = data[2];
        uint8_t rightStickY = data[3];
        uint8_t buttons = data[4];
        
        Serial.printf("左摇杆: X=%d, Y=%d\n", leftStickX, leftStickY);
        Serial.printf("右摇杆: X=%d, Y=%d\n", rightStickX, rightStickY);
        Serial.printf("按钮: 0x%02X\n", buttons);
        
        // 根据摇杆数据控制小车
        // 将摇杆值转换为PWM控制信号
        int leftStickXCentered = leftStickX - 128;  // 中心化摇杆值 (-128 到 127)
        int leftStickYCentered = leftStickY - 128;
        
        // 使用左摇杆Y轴控制前进后退
        if (abs(leftStickYCentered) > 20) {  // 死区
          controllerSpeed = map(abs(leftStickYCentered), 0, 127, 0, 100);
          if (leftStickYCentered < 0) {
            // 前进
            Serial.printf("前进，速度: %d\n", controllerSpeed);
          } else {
            // 后退
            controllerSpeed = -controllerSpeed;
            Serial.printf("后退，速度: %d\n", controllerSpeed);
          }
        } else {
          controllerSpeed = 0;  // 死区内停止
        }
        
        // 使用左摇杆X轴控制转向
        if (abs(leftStickXCentered) > 20) {  // 死区
          controllerTurn = map(abs(leftStickXCentered), 0, 127, 0, 50);
          if (leftStickXCentered < 0) {
            // 左转
            controllerTurn = -controllerTurn;
            Serial.printf("左转，转向: %d\n", controllerTurn);
          } else {
            // 右转
            Serial.printf("右转，转向: %d\n", controllerTurn);
          }
        } else {
          controllerTurn = 0;  // 死区内不转向
        }
        
        // 处理按钮
        if (buttons & 0x01) {
          Serial.println("按钮A被按下 - 切换控制模式");
          controllerEnabled = !controllerEnabled;
          if (controllerEnabled) {
            Serial.println("启用手柄控制模式");
          } else {
            Serial.println("启用平衡控制模式");
            // 重置控制值
            controllerSpeed = 0;
            controllerTurn = 0;
          }
        }
        if (buttons & 0x02) {
          Serial.println("按钮B被按下 - 紧急停止");
          controllerSpeed = 0;
          controllerTurn = 0;
          // 可以添加其他紧急停止逻辑
        }
      }
    }
  }
}

/*-------检查蓝牙连接状态-------*/
void checkBluetoothConnection() {
  static unsigned long lastCheckTime = 0;
  const unsigned long checkInterval = 5000;  // 每5秒检查一次
  
  if (millis() - lastCheckTime > checkInterval) {
    lastCheckTime = millis();
    
    if (controllerConnected && !SerialBT.connected()) {
      Serial.println("蓝牙连接断开，尝试重新连接...");
      controllerConnected = false;
      controllerEnabled = false;
      
      // 尝试重新连接
      if (xiaomiControllerFound && xiaomiControllerDevice != nullptr) {
        if (connectToXiaomiController()) {
          Serial.println("重新连接成功！");
        } else {
          Serial.println("重新连接失败");
        }
      }
    }
  }
}

void initBluetooth() {
  // 初始化蓝牙串口
  if (!SerialBT.begin("ESP32_BalanceCar")) {
    Serial.println("蓝牙初始化失败!");
    return;
  } else {
    Serial.println("蓝牙设备已启动，等待连接...");
    Serial.print("Starting discoverAsync...");
    if (SerialBT.discoverAsync(btAdvertisedDeviceFound)) {
      Serial.println("Findings will be reported in \"btAdvertisedDeviceFound\"");
      delay(20000);
      Serial.print("Stopping discoverAsync... ");
      SerialBT.discoverAsyncStop();
      Serial.println("stopped");
      
      // 搜索完成后尝试连接小米手柄
      if (xiaomiControllerFound) {
        Serial.println("发现小米手柄，尝试连接...");
        if (connectToXiaomiController()) {
          Serial.println("小米手柄连接成功！");
        } else {
          Serial.println("小米手柄连接失败，继续等待其他设备连接...");
        }
      } else {
        Serial.println("未发现小米手柄，继续等待其他设备连接...");
      }
    } else {
      Serial.println("Error on discoverAsync f.e. not workin after a \"connect\"");
    }
  }
}

void Blink() {
  digitalWrite(2, HIGH);  // turn the LED on (HIGH is the voltage level)
  delay(50);              // wait for a second
  digitalWrite(2, LOW);   // turn the LED off by making the voltage LOW
  delay(50);              // wait for a second
}

#include <ESP8266WiFi.h>
#include <WiFiUdp.h>
#include <string.h>
#include <SoftwareSerial.h>

//6\7\8\9\10\11
//2\3\4\5\12\13\14\15
//1

SoftwareSerial ArduinoSerial(12, 13);

//wifi
WiFiUDP Udp;
unsigned int localUdpPort = 5555;  // local port to listen on
unsigned int remoteUdpPort = 8888;  // local port to listen on
IPAddress local_IP(192,168,1,88);
IPAddress gateway(192,168,1,1);
IPAddress subnet(255,255,255,0);
char incomingPacket[255];  // buffer for incoming packets
char replyPacket[] = "test ok";  // a reply string to send back
//wifi end 


void setup() {
  Serial.begin(9600);
  ArduinoSerial.begin(9600);
  pinMode(2, OUTPUT);
  initWifi_Ap();
}

void loop() {
  // if (ArduinoSerial.available() > 0) {
  //   String receivedString = ArduinoSerial.readString();
  //   Serial.println("receivedString");
  // }
  Serial.println("aaaa");
  ArduinoSerial.write("1");

  boolean haveCommand = GetCommand();

  if (haveCommand) {
    Blink();
  } else {
    delay(100);
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
    while (token!= NULL) {
        result[wordIndex++] = String(token);
        token = strtok(NULL, " ");
    }
    resultSize = wordIndex;
}

boolean GetCommand() {
  int packetSize = Udp.parsePacket();
  if (packetSize)
  {
    // receive incoming UDP packets
    Serial.printf("Received %d bytes from %s, port %d\n", packetSize, Udp.remoteIP().toString().c_str(), Udp.remotePort());
    int len = Udp.read(incomingPacket, 255);
    if (len > 0)
    {
      incomingPacket[len] = 0;
    }
    Serial.printf("UDP packet contents: %s\n", incomingPacket);

    //get command
    String commandAndArgs[10];  // 假设最多 10 个
    int count;

    splitString(incomingPacket, commandAndArgs, count);

    char command = commandAndArgs[0][0];
    int argsCount = 0;
    int args[argsCount];
    if (count > 0) {
      argsCount = count - 1;
      
      for (int i=1; i<count; i++) {
        args[i-1] = stringToInt(commandAndArgs[i]);
      }
    }
    
    onCommand(command, args, argsCount);
    return true;
  }

  return false;
}

//command 命令
//args 用一个字符串表示，一般情况是发送一些log信息
void sendCommand(int command, String args) {
  Serial.print("===sendCommand====");
  Serial.println(command);


  char charArray[args.length() + 1];
  args.toCharArray(charArray, args.length() + 1);

  int size = args.length() + 1 + 5;
  char buffer[size];  // 用于存储格式化后的字符串
  sprintf(buffer, "%d %s", command, charArray);

  Serial.print(buffer);

  Udp.beginPacket(Udp.remoteIP(), remoteUdpPort);
  Udp.write(buffer);
  Udp.endPacket();
}

void onCommand(char command, int* args, int argsCount) {
  Serial.print("===onCommand====");
  Serial.println(command);

  for (int i=0; i<argsCount; i++) {
    Serial.print(args[i]);
    Serial.print(" ");
  }
  Serial.println("");

  switch(command) {
    case '0': sendCommand(100, "test ok"); break; //手机连接发送连接信号，发送100表示成功
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

void initWifi_Ap() {
  Serial.print("Setting soft-AP ... ");
  WiFi.softAPConfig(local_IP, gateway, subnet);

  boolean result = WiFi.softAP("ESPsoftAP_01", "12312331");
  if(result == true)
  {
    Serial.println("Ready");
    Udp.begin(localUdpPort);
    Serial.printf("Now listening at IP %s, UDP port %d\n", WiFi.softAPIP().toString().c_str(), localUdpPort);
  }
  else
  {
    Serial.println("Failed!");
  }
}

void Blink() {
  digitalWrite(2, HIGH);  // turn the LED on (HIGH is the voltage level)
  delay(50);                      // wait for a second
  digitalWrite(2, LOW);   // turn the LED off by making the voltage LOW
  delay(50);                      // wait for a second
}




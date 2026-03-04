# Android ROS HMI Controller 🤖📱

A modern, robust Android **Human-Machine Interface (HMI)** built with **Kotlin** and **Jetpack Compose**.
This application serves as the primary control tablet for a **ROS-based robotic system (Raspberry Pi + PSoC microcontroller)**, offering both **manual override control** and **Excel-driven automated trajectory execution**.

---

## ✨ Key Features

### 🔌 Industrial-Grade Networking

**mDNS Auto-Discovery**
Automatically scans the local network to find the robot's IP address without manual entry.

**Network Binding**
Forces Android OS to route traffic through the robot’s Wi-Fi hotspot, preventing fallback to cellular data when there is no internet.

**WebSocket ROS Bridge**
Maintains a persistent low-latency duplex connection to the Raspberry Pi using `rosbridge_server` (Port **9090**).

**App-Level Authentication**
Secure login gateway preventing unauthorized WebSocket connection attempts.

---

### 🕹️ Manual Control & Emergency Stop

**Real-Time Telemetry**
Sends **Target Radius and Angle** while simultaneously receiving real robot feedback from **PSoC encoders**.

**Live Error Calculation**
Dynamically calculates **Radius and Angle error percentages** in real time.

**Hardware E-Stop**
Dedicated **Abort button** instantly publishes a `(0,0)` vector to halt all motors.

---

### 📊 Excel-Driven Automation

**Trajectory Parsing**
Imports `.xlsx` files containing sequential movement commands:

* Radius
* Angle
* Delay

**Live Data Table**
Displays execution steps in a **Jetpack Compose table UI** updating with live telemetry and error margins.

**CSV Reporting**
Exports test results *(Target vs Actual + Error %)* to `.csv` for analysis and quality assurance.

---

### 🛠️ Advanced System Diagnostics

**Raw Firmware Intercept**
Captures raw serial logs such as:

# ERROR: I2C failed

generated directly from the **PSoC firmware**.

**Smart Categorization**

* Critical hardware failures → **UI Snackbars**
* Background INIT/WARN logs → **Scrollable diagnostics console**

**OS-Level Shutdown**

Dedicated UI workflow publishes a **Linux shutdown command** to the Raspberry Pi to prevent **SD card corruption**.

---

## 🛠 Tech Stack & Architecture

| Component               | Technology                           |
| ----------------------- | ------------------------------------ |
| Language                | Kotlin                               |
| UI Framework            | Jetpack Compose (Material Design 3)  |
| Concurrency             | Kotlin Coroutines + Flow (StateFlow) |
| Networking              | OkHttp WebSockets                    |
| Android Networking APIs | ConnectivityManager, NsdManager      |
| Excel Parsing           | Apache POI                           |
| JSON                    | kotlinx.serialization                |
| Robot Middleware        | ROS 1 / ROS 2 using rosbridge_suite  |

---

## 📡 ROS Topic Reference (Hardware Integration)

The application communicates with ROS via **rosbridge_server**.

| Topic                 | Action    | Message Type      | Description                                                  |
| --------------------- | --------- | ----------------- | ------------------------------------------------------------ |
| `/manual_command`     | Publish   | JSON              | Sends `{radius: Float, angle: Float}`                        |
| `/automation_command` | Publish   | JSON              | Sends trajectory steps `{slNo, radius, angle, delaySeconds}` |
| `/sys_command`        | Publish   | std_msgs/String   | Sends `"SHUTDOWN"` to safely power off the Raspberry Pi      |
| `/telemetry`          | Subscribe | JSON / Raw String | Receives robot movement feedback or raw PSoC error logs      |

---

## 🚀 Setup & Installation

### Clone the Repository

git clone https://github.com/ziymaxcode/ROSBOT-Controller.git

### Open in Android Studio

Open the cloned directory using **Android Studio (Giraffe or newer recommended)**.

### Build the Project

Allow **Gradle** to sync and download dependencies:

* Jetpack Compose
* OkHttp
* Apache POI

### Deploy to Device

Connect your **Android tablet or phone** via:

* USB debugging
  or
* Wi-Fi debugging

Then click **Run** in Android Studio.

---

## 🧪 Hardware Requirements for Testing

To fully test this application:

1. Connect the Android device to a **Wi-Fi hotspot hosted by the Raspberry Pi**.
2. Ensure the robot is running:

rosbridge_websocket

on port **9090**.

---

## 📷 System Architecture

Android App (HMI)
↓ WebSocket (rosbridge)
Raspberry Pi (ROS)
↓ Serial / I2C
PSoC Microcontroller
↓
Motor Controllers & Sensors

---

## 📄 License

This project is intended for **robotics research, testing, and industrial HMI development**.

---


Designed and developed for **seamless robotics testing and human-machine interaction**.

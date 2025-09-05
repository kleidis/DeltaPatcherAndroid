# Delta Patcher Android
![CI Build Status](https://github.com/innixunix/DeltaPatcherAndroid/actions/workflows/build.yml/badge.svg)
![GitHub Downloads](https://img.shields.io/github/downloads/innixunix/DeltaPatcherAndroid/total?logo=github&label=GitHub%20Downloads) 
<a href="https://www.buymeacoffee.com/nixynx" target="_blank"><img src="https://cdn.buymeacoffee.com/buttons/default-orange.png" alt="Buy Me A Coffee" height="30" width="130"></a>

<p align="center">
  <img src="https://github.com/user-attachments/assets/b752635a-7281-4de6-956c-9e0cd78552c5" 
       alt="App Screenshot" 
       width="200" 
       style="border-radius:20px; box-shadow:0 4px 12px rgba(0,0,0,0.3);" />
</p>

**Delta Patcher Android** is an android "fork" of marco-calautti's DeltaPatcher.

This is an (android) GUI software that is able to create and apply xdelta patches as the ones supported by the xdelta tool developed by Joshua McDonald.

Thanks to marco-calautti for the original implantation  which was heavily used by me as a reference for the Android software


<div style="display:inline-block; background:#fff3cd; color:#856404; border:1px solid #ffeeba; border-radius:20px; padding:6px 12px; font-weight:600; font-size:14px; margin:6px 0;">
⚠️ This project is not yet finished — please report any bugs you encounter! Also feel free to share any ideas you have that could improve the project
</div>

---------------------------------
## Usage notes

- Due to the way Android Storage APIs work, ROMs / patches with large file sizes will take a semi-long amount of time to patch.

  - The app should be able to work in the background.

- It is recommended to exit the app via the exit button near the settings icon or via the notification exit button.

  - If the app's cache builds up, just do one of the above or clear it manually.## Building

## Building

#### Clone the Repository
```bash
git clone https://github.com/yourusername/DeltaPatcherAndroid.git --recursive
cd DeltaPatcherAndroid
```

#### Open in Android Studio
1. Launch Android Studio
2. Select **"Open an existing Android Studio project"**
3. Navigate to the cloned repository and click **OK**
4. Wait for Gradle sync to complete

#### Build 
Build → Make Project (Ctrl+F9)

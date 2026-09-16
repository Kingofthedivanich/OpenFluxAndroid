APP_ABI      			:= armeabi-v7a arm64-v8a x86 x86_64
APP_PLATFORM 			:= android-21
APP_STL      			:= c++_static
# Android 15+ 16KB page-size compatibility: align ELF LOAD segments.
APP_LDFLAGS  			+= -Wl,-z,max-page-size=16384

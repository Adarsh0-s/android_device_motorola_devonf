#
# Copyright (C) 2024 PixelOS
#
# SPDX-License-Identifier: Apache-2.0
#

# Inherit from those products. Most specific first.
$(call inherit-product, $(SRC_TARGET_DIR)/product/core_64_bit.mk)
$(call inherit-product, $(SRC_TARGET_DIR)/product/full_base_telephony.mk)

# Inherit from device makefile.
$(call inherit-product, device/motorola/devonf/device.mk)

# Inherit some common LineageOS stuff.
$(call inherit-product, vendor/lineage/config/common_full_phone.mk)
TARGET_BOOT_ANIMATION_RES := 1080

PRODUCT_NAME := lineage_devonf
PRODUCT_DEVICE := devonf
PRODUCT_MANUFACTURER := motorola
PRODUCT_BRAND := motorola
PRODUCT_MODEL := moto g73 5G 

PRODUCT_GMS_CLIENTID_BASE := android-motorola

TARGET_ENABLE_BLUR := true
WITH_GMS := false
RISING_MAINTAINER := Adarsh
TARGET_DEFAULT_PIXEL_LAUNCHER := false
TARGET_PREBUILT_LAWNCHAIR_LAUNCHER := true

PRODUCT_BUILD_PROP_OVERRIDES += \
    DeviceName=devonf \
    BuildDesc="devonf_g_sys-user 12 U1TNS34.82-12-7-8 4f333 release-keys" \
BuildFingerprint=motorola/devonf_g_sys/devonf:14/U1TNS34M.82-12-7-8/4f333:user/release-keys

PRODUCT_BUILD_PROP_OVERRIDES += \
    RisingChipset="Dimensity 930" \
    RisingMaintainer="Adarsh"

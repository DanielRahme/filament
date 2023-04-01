##!/bin/bash

CC=/usr/bin/clang CXX=/usr/bin/clang++ CXXFLAGS=-stdlib=libc++ ./build.sh -k sample-suzanne-offscreen,sample-lucy-offscreen,sample-suzanne-vk-offscreen,sample-lucy-vk-offscreen -q arm64-v8a -p android -i release

export APKSIGNER=$ANDROID_HOME/build-tools/33.0.1/apksigner

${APKSIGNER} sign --ks my-release-key.keystore out/sample-suzanne-offscreen-release.apk
${APKSIGNER} sign --ks my-release-key.keystore out/sample-lucy-offscreen-release.apk
${APKSIGNER} sign --ks my-release-key.keystore out/sample-suzanne-vk-offscreen-release.apk
${APKSIGNER} sign --ks my-release-key.keystore out/sample-lucy-vk-offscreen-release.apk

adb install-multi-package -r -g out/sample-suzanne-offscreen-release.apk out/sample-lucy-offscreen-release.apk out/sample-suzanne-vk-offscreen-release.apk out/sample-lucy-vk-offscreen-release.apk

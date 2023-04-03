##!/bin/bash

CC=/usr/bin/clang CXX=/usr/bin/clang++ CXXFLAGS=-stdlib=libc++ ./build.sh -k sample-suzanne,sample-lucy,sample-suzanne-vk,sample-lucy-vk -q arm64-v8a -p android -i release

export APKSIGNER=$ANDROID_HOME/build-tools/33.0.1/apksigner

${APKSIGNER} sign --ks my-release-key.keystore out/sample-suzanne-release.apk
${APKSIGNER} sign --ks my-release-key.keystore out/sample-lucy-release.apk
${APKSIGNER} sign --ks my-release-key.keystore out/sample-suzanne-vk-release.apk
${APKSIGNER} sign --ks my-release-key.keystore out/sample-lucy-vk-release.apk

adb install-multi-package -r -g out/sample-suzanne-release.apk out/sample-lucy-release.apk out/sample-suzanne-vk-release.apk out/sample-lucy-vk-release.apk

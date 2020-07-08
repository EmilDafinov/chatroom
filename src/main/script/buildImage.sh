#!/bin/bash

set -e -x -u

IMAGE_NAME_SPLIT=(${IMAGE//:/ })
IMAGE_VERSION="${IMAGE_NAME_SPLIT[1]}"

sbt "set version in ThisBuild := \"${IMAGE_VERSION}\"" "docker:publishLocal"

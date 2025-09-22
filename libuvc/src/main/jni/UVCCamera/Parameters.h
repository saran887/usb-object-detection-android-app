/*
 * UVCCamera
 * library and sample to access to UVC web camera on non-rooted Android device
 *
 * Copyright (c) 2015-2017 saki t_saki@serenegiant.com
 *
 * File name: Parameters.h
 *

*/
#ifndef PARAMETERS_H_
#define PARAMETERS_H_

#pragma interface

#include "libUVCCamera.h"

class UVCDiags {
private:
public:
	UVCDiags();
	~UVCDiags();
	char *getDescriptions(const uvc_device_handle_t *deviceHandle);
	char *getCurrentStream(const uvc_stream_ctrl_t *ctrl);
	char *getSupportedSize(const uvc_device_handle_t *deviceHandle);
};

#endif /* PARAMETERS_H_ */

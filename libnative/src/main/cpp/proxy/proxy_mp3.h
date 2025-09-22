
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 *
 * Proxy of mp3.
 *

 */
#ifndef ANDROIDUSBCAMERA_PROXY_MP3_H
#define ANDROIDUSBCAMERA_PROXY_MP3_H

#ifdef __cplusplus
extern "C" {
#endif

#include <jni.h>

void lameInit(JNIEnv *env, jobject instance, jint inSampleRate, jint outChannel, jint outSampleRate, jint outBitRate, jint quality);
jint lameEncode(JNIEnv *env, jobject instance, jshortArray leftBuf_, jshortArray rightBuf, jint sampleRate, jbyteArray mp3Buf);
jint lameFlush(JNIEnv *env, jobject instance, jbyteArray mp3Buf);
void lameClose(JNIEnv *env, jobject instance);

#ifdef __cplusplus
};
#endif

#endif //ANDROIDUSBCAMERA_PROXY_MP3_H

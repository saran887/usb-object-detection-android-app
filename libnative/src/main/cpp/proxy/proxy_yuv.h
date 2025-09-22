
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 *
 * Proxy of yuv.
 *

 */
#ifndef ANDROIDUSBCAMERA_PROXY_YUV_H
#define ANDROIDUSBCAMERA_PROXY_YUV_H
#ifdef __cplusplus
extern "C" {
#endif

#include <jni.h>
#include <cstdlib>
#include "../module/yuv/yuv.h"
#include "../utils/logger.h"

void yuv420spToNv21(JNIEnv *env, jobject instance, jbyteArray data, jint width, jint height);
void nv21ToYuv420sp(JNIEnv *env, jobject instance, jbyteArray data, jint width, jint height);
void nv21ToYuv420spWithMirror(JNIEnv *env, jobject instance, jbyteArray data, jint width, jint height);
void nv21ToYuv420p(JNIEnv *env, jobject instance, jbyteArray data, jint width, jint height);
void nv21ToYuv420pWithMirror(JNIEnv *env, jobject instance, jbyteArray data, jint width, jint height);
void nativeRotateNV21(JNIEnv *env, jobject instance, jbyteArray data, jint width, jint height, jint degree);

#ifdef __cplusplus
};
#endif
#endif //ANDROIDUSBCAMERA_PROXY_YUV_H

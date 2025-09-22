
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 *
 * Lame for mp3
 *

 */

#ifndef ANDROIDUSBCAMERA_MP3_H
#define ANDROIDUSBCAMERA_MP3_H

#ifdef __cplusplus
extern "C" {
#endif
void lameInitInternal(int inSampleRate, int outChannel, int outSampleRate, int outBitRate, int quality);
int lameEncodeInternal(short* leftBuf, short* rightBuf, int sampleRate, unsigned char* mp3Buf, int len);
int lameFlushInternal(unsigned char* mp3Buf, int len);
void lameCloseInternal();
#ifdef __cplusplus
};
#endif
#endif //ANDROIDUSBCAMERA_MP3_H

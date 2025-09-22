
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package com.jiangdg.ausbc.camera.bean

/** uvc camera info
 *
 * @author Created by jiangdg on 2022/1/27
 */
@kotlin.Deprecated("Deprecated since version 3.3.0")
class CameraUvcInfo(override val cameraId: String) : CameraInfo(cameraId) {
    var cameraName: String = ""
    var cameraProductName: String? = null
    var cameraManufacturerName: String? = null
    var cameraProtocol: Int = 0
    var cameraClass: Int = 0
    var cameraSubClass: Int = 0

    override fun toString(): String {
        return "CameraUvcInfo(cameraId='$cameraId', " +
                "cameraName='$cameraName', " +
                "cameraProductName='$cameraProductName', " +
                "cameraManufacturerName='$cameraManufacturerName', " +
                "cameraProtocol=$cameraProtocol, " +
                "cameraClass=$cameraClass, " +
                "cameraSubClass=$cameraSubClass, " +
                "cameraVid=$cameraVid, " +
                "cameraPid=$cameraPid, " +
                "cameraPreviewSizes=$cameraPreviewSizes)"

    }
}

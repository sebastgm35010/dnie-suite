package android.net

import android.os.Parcel

class TestUri(private val uriString: String = "content://test/missing") : Uri() {
    override fun isHierarchical() = true
    override fun isRelative() = false
    override fun getScheme() = "content"
    override fun getSchemeSpecificPart() = "//test/missing"
    override fun getEncodedSchemeSpecificPart() = "//test/missing"
    override fun getAuthority() = "test"
    override fun getEncodedAuthority() = "test"
    override fun getUserInfo(): String? = null
    override fun getEncodedUserInfo(): String? = null
    override fun getHost() = "test"
    override fun getPort() = -1
    override fun getPath() = "/missing"
    override fun getEncodedPath() = "/missing"
    override fun getQuery(): String? = null
    override fun getEncodedQuery(): String? = null
    override fun getFragment(): String? = null
    override fun getEncodedFragment(): String? = null
    override fun getPathSegments(): List<String> = listOf("missing")
    override fun getLastPathSegment() = "missing"
    override fun buildUpon(): Builder? = null
    override fun writeToParcel(dest: Parcel, flags: Int) {}
    override fun describeContents() = 0
    override fun compareTo(other: Uri) = 0
    override fun toString() = uriString
}

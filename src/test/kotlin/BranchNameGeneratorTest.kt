import com.github.alexlandau.ghss.autogenerateBranchName
import com.github.alexlandau.ghss.isValidGhBranchName
import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.Test

class BranchNameGeneratorTest {
    @Test
    fun testBranchNameAutogenerator() {
        val generate = { title: String -> autogenerateBranchName(title, null, { false }) }
        assertEquals("this-is-an-example-commit-title", generate("This is an example commit title."))
        assertEquals("improvement-and-support", generate("[improvement] //, /* */, and # support"))
        assertEquals("0123456789012345678901234567890123456789", generate("0123456789012345678901234567890123456789toolong"))
        assertEquals("this-is-an-example-particularly-long-com", generate("This is an example, particularly long commit title."))
    }

    @Test
    fun testIsValidGhBranchName() {
        // TODO: I haven't actually looked up the actual GH branch name requirements
        assertTrue(isValidGhBranchName("ok"))
        assertTrue(isValidGhBranchName("just-some-normal-name"))
        assertTrue(isValidGhBranchName("username/just-some-normal-name"))
        assertTrue(isValidGhBranchName("username/category/still-a-normal-name"))
        assertTrue(isValidGhBranchName("hyphenated-category/still-a-normal-name"))

//        assertFalse(isValidGhBranchName("username/category/but-this-one-is-just-way-too-long-unfortunately"))
        assertFalse(isValidGhBranchName("-hyphen-at-start"))
        assertFalse(isValidGhBranchName("hyphen-at-end-"))
        assertFalse(isValidGhBranchName("double--hyphens"))
        assertFalse(isValidGhBranchName(""))
        assertFalse(isValidGhBranchName("/starts-with-a-slash"))
        assertFalse(isValidGhBranchName("ends-with-a-slash/"))
        assertFalse(isValidGhBranchName("double//slash"))
        assertFalse(isValidGhBranchName("slash/-and-hyphen"))
        assertFalse(isValidGhBranchName("hyphen-/and-slash"))
        assertFalse(isValidGhBranchName("bad character"))
    }
}

//#[test]
//fn test_generate_chosen_name() {
//    assert_eq!("this-is-an-example-commit-title", generate_chosen_name("This is an example commit title."));
//    assert_eq!("improvement-and-support", generate_chosen_name("[improvement] //, /* */, and # support"));
//    assert_eq!("this-is-an-example-particularly-long-comm", generate_chosen_name("This is an example, particularly long commit title."));
//}
//
//#[test]
//fn test_is_valid_gh_branch_name() {
//    assert!(is_valid_gh_branch_name("ok"));
//    assert!(is_valid_gh_branch_name("just-some-normal-name"));
//    assert!(is_valid_gh_branch_name("username/just-some-normal-name"));
//    assert!(is_valid_gh_branch_name("username/category/still-a-normal-name"));
//    assert!(is_valid_gh_branch_name("hyphenated-category/still-a-normal-name"));
//
//    assert!(!is_valid_gh_branch_name("username/category/but-this-one-is-just-way-too-long-unfortunately"));
//    assert!(!is_valid_gh_branch_name("-hyphen-at-start"));
//    assert!(!is_valid_gh_branch_name("hyphen-at-end-"));
//    assert!(!is_valid_gh_branch_name("double--hyphens"));
//    assert!(!is_valid_gh_branch_name(""));
//    assert!(!is_valid_gh_branch_name("/starts-with-a-slash"));
//    assert!(!is_valid_gh_branch_name("ends-with-a-slash/"));
//    assert!(!is_valid_gh_branch_name("double//slash"));
//    assert!(!is_valid_gh_branch_name("slash/-and-hyphen"));
//    assert!(!is_valid_gh_branch_name("hyphen-/and-slash"));
//    assert!(!is_valid_gh_branch_name("bad character"));
//}
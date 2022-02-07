import com.github.alexlandau.ghss.autogenerateBranchName
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test

class BranchNameGeneratorTest {
    @Test
    fun testBranchNameAutogenerator() {
        val generate = { title: String -> autogenerateBranchName(title, { false }) }
        assertEquals("this-is-an-example-commit-title", generate("This is an example commit title."))
        assertEquals("improvement-and-support", generate("[improvement] //, /* */, and # support"))
        assertEquals("0123456789012345678901234567890123456789", generate("0123456789012345678901234567890123456789toolong"))
        assertEquals("this-is-an-example-particularly-long-com", generate("This is an example, particularly long commit title."))
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
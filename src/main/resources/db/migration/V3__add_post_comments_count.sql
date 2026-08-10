/*
 * Store the number of comments directly on a post so every feed response
 * contains an accurate count immediately after page load.
 */
ALTER TABLE posts
    ADD COLUMN comments_count BIGINT NOT NULL DEFAULT 0;

/* Backfill counters for comments that existed before this migration. */
UPDATE posts AS post
SET comments_count = (
    SELECT COUNT(*)
    FROM comments AS comment
    WHERE comment.post_id = post.id
);

/*
 * Keep the counter correct for every insert, delete or reassignment.
 * A database trigger also covers bulk deletions performed by cleanup flows.
 */
CREATE OR REPLACE FUNCTION update_post_comments_count()
RETURNS TRIGGER
LANGUAGE plpgsql
AS $$
BEGIN
    IF TG_OP = 'INSERT' THEN
        UPDATE posts
        SET comments_count = comments_count + 1
        WHERE id = NEW.post_id;

        RETURN NEW;
    END IF;

    IF TG_OP = 'DELETE' THEN
        UPDATE posts
        SET comments_count = GREATEST(comments_count - 1, 0)
        WHERE id = OLD.post_id;

        RETURN OLD;
    END IF;

    IF TG_OP = 'UPDATE'
       AND NEW.post_id IS DISTINCT FROM OLD.post_id THEN
        UPDATE posts
        SET comments_count = GREATEST(comments_count - 1, 0)
        WHERE id = OLD.post_id;

        UPDATE posts
        SET comments_count = comments_count + 1
        WHERE id = NEW.post_id;
    END IF;

    RETURN NEW;
END;
$$;

CREATE TRIGGER comments_update_post_count
AFTER INSERT OR DELETE OR UPDATE OF post_id
ON comments
FOR EACH ROW
EXECUTE FUNCTION update_post_comments_count();

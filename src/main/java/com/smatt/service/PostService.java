package com.smatt.service;

import com.smatt.config.Constants;
import com.smatt.dao.CategoryRepository;
import com.smatt.dao.PostRepository;
import com.smatt.models.Post;
import com.smatt.models.User;
import org.apache.commons.lang3.StringUtils;
import org.apache.log4j.Logger;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;
import org.springframework.ui.ModelMap;
import org.springframework.web.multipart.MultipartFile;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

import javax.servlet.http.HttpSession;
import java.util.Set;
import java.util.concurrent.CompletableFuture;

/**
 * Created by smatt on 12/06/2017.
 *
 */
@Service
public class PostService {

    private PostRepository postRepository;
    private CategoryRepository categoryRepository;
    private StorageService storageService;
    private Logger logger = Logger.getLogger(PostService.class);

    @Autowired
    public PostService(
            PostRepository postRepository, StorageService storageService,
            CategoryRepository categoryRepository
    ) {
        this.postRepository = postRepository;
        this.storageService = storageService;
        this.categoryRepository = categoryRepository;
    }

    /*
    * This method will be used to update post object Async
    * especially increasing the views count when a post is read
    * */
    @Async
    public CompletableFuture<Post> updatePostAsyn(Post post) {
        return CompletableFuture.completedFuture(postRepository.save(post));
    }


    /*
    * This is a service method to save
     * it accepts the incoming post popula
    * */
    public ModelMap savePost(Post post, String category, MultipartFile file, HttpSession session) {

        ModelMap map = new ModelMap();

        if(!StringUtils.isEmpty(post.getId())) {
            //updating
            Post existP = postRepository.findOne(post.getId());
            logger.info("existing post = " + existP.toString());
            existP.setTitle(post.getTitle());
            existP.setPost(post.getPost());
            existP.setCategory(categoryRepository.findOne(category));
            existP.setTags(post.getTags());
            existP.setPublished(post.isPublished());
            existP.setFeatured(post.isFeatured());

            if(!StringUtils.isEmpty(post.getCoverPic()) && !StringUtils.equals(existP.getCoverPic(), post.getCoverPic()) && !file.isEmpty()) {
                //logger.info("coverPic changed called");
                //I have changed the coverpic while updating so delete the old one
                String oldPix = existP.getCoverPic();
                existP.setCoverPic(storageService.store(file));
                if(!StringUtils.isEmpty(oldPix)) storageService.delete(oldPix);
            }
            map.addAttribute("post", postRepository.save(existP));
            map.addAttribute("status", "Post Updated Successfully");
            map.addAttribute("url", "redirect:/eyin/posts/read/" + existP.getId());
        }
        else {
            //saving afresh
            if(file != null && !file.isEmpty()) {
                post.setCoverPic(storageService.store(file));
            }
            post.setAuthor( ((User) session.getAttribute(Constants.LOGGED_IN_USER)));
            post.setCategory(categoryRepository.findOne(category));
            Post savedPost = postRepository.save(post);
            logger.info("Newly saved post obj ==\n " + savedPost.toString());
            map.addAttribute("post", savedPost);
            map.addAttribute("status", "Post Saved Successfully");
            map.addAttribute("url", "redirect:/eyin/posts/read/" + savedPost.getId());
        }

        return map;
    }


    public ModelMap validatePost(Post post, RedirectAttributes attr, MultipartFile file, String index) {

        ModelMap modelMap = new ModelMap();
        modelMap.addAttribute("url", "redirect:"+index+"/edit");

        if(!post.validate()) {
            modelMap.addAttribute("post", post);
            modelMap.addAttribute("status", false);
            modelMap.addAttribute("error", "Some Important Parameters are missing. Post must have title and post body");
            modelMap.addAttribute("attr", attr);
            return modelMap;
        }

        if(file != null && !file.isEmpty() && !file.getContentType().contains("image")) {
            modelMap.addAttribute("post", post);
            modelMap.addAttribute("error", "Only Image files are allowed kindly try again please");
            modelMap.addAttribute("status", false);
            modelMap.addAttribute("attr", attr);
            return modelMap;
        }

        modelMap.addAttribute("status", true);
        return modelMap;
    }


    public ModelMap showPaginatedPostList(ModelMap modelMap, Page<Post> posts, String title, String pageLink) {

        int totalPages = posts.getTotalPages();
        int currentPage = posts.getNumber();
        //        logger.info("currentPage = " + currentPage);
        modelMap.addAttribute("title", title);
        modelMap.addAttribute("posts", posts);
        modelMap.addAttribute("currentPage", (currentPage + 1));
        modelMap.addAttribute("totalPages", totalPages);
        if( (currentPage + 1) < totalPages )
            modelMap.addAttribute("nextLink", pageLink + "/" + (currentPage + 1)); //older posts
        if( (currentPage - 1) >= 0 )
            modelMap.addAttribute("prevLink", pageLink + "/" + (currentPage - 1)); //newer posts

        modelMap.addAttribute("trendingPosts",  postRepository.findAllPublishedPosts(
                new PageRequest(0, 4, new Sort(Sort.Direction.DESC, "views"))) );

        return modelMap;
    }
}

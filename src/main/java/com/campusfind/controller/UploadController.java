package com.campusfind.controller;

import com.campusfind.entity.*;
import com.campusfind.repository.*;
import com.campusfind.service.*;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;
import org.springframework.http.*;
import org.springframework.web.server.ResponseStatusException;
import java.nio.file.*;
import java.security.Principal;
import java.util.*;
import javax.imageio.*;
import javax.imageio.stream.ImageInputStream;
import java.awt.image.BufferedImage;

@RestController
@RequestMapping("/api/uploads")
public class UploadController {
    private final UploadRepository uploads;private final ItemRepository items;private final ClaimRepository claims;private final Support support;
    private final Path root;
    public UploadController(UploadRepository uploads,ItemRepository items,ClaimRepository claims,Support support,@org.springframework.beans.factory.annotation.Value("${campus.storage-dir:.local/uploads}") String storage){this.uploads=uploads;this.items=items;this.claims=claims;this.support=support;this.root=Path.of(storage).toAbsolutePath().normalize();}
    @PostMapping(consumes=MediaType.MULTIPART_FORM_DATA_VALUE)
    public Object upload(@RequestParam MultipartFile file,@RequestParam(defaultValue="ITEM") String purpose,Principal p)throws Exception {
        UserAccount user=support.user(p);ItemService.check(Set.of("ITEM","EVIDENCE","PROFILE").contains(purpose),"Invalid photo purpose");ItemService.check(file.getSize()>0&&file.getSize()<=5*1024*1024,"Choose an image smaller than 5 MB");
        String name=file.getOriginalFilename()==null?"image":Path.of(file.getOriginalFilename()).getFileName().toString();ItemService.check(name.toLowerCase().matches(".*\\.(png|jpe?g)$"),"Only JPG and PNG images are supported");
        long unattached=uploads.findAll().stream().filter(u->u.owner.id.equals(user.id)&&u.itemId==null&&u.claimId==null).count();ItemService.check(unattached<30,"You have 30 unused uploads. Use an existing image or remove an unused one first.");
        BufferedImage decoded;try(ImageInputStream stream=ImageIO.createImageInputStream(file.getInputStream())) {Iterator<ImageReader> readers=ImageIO.getImageReaders(stream);ItemService.check(readers.hasNext(),"The file is not a readable image");ImageReader reader=readers.next();try{reader.setInput(stream);String format=reader.getFormatName();ItemService.check(format.equalsIgnoreCase("JPEG")||format.equalsIgnoreCase("PNG"),"Only actual JPG and PNG images are supported");ItemService.check((long)reader.getWidth(0)*reader.getHeight(0)<=20_000_000,"Photo is too large; use at most 20 megapixels");decoded=reader.read(0);}finally{reader.dispose();}}
        Files.createDirectories(root);String id=UUID.randomUUID().toString();Path path=root.resolve(id+".png");ImageIO.write(decoded,"png",path.toFile());
        Upload u=new Upload();u.id=id;u.owner=user;u.purpose=purpose;u.path=path.toString();u.mimeType="image/png";u.originalName=name.length()>150?name.substring(name.length()-150):name;uploads.save(u);support.audit(user,"IMAGE_UPLOADED","upload:"+id);return Map.of("id",id,"url","/api/uploads/"+id,"name",u.originalName);
    }
    @GetMapping("/{id}") public ResponseEntity<byte[]> read(@PathVariable String id,Principal p)throws Exception {
        Upload u=uploads.findById(id).orElseThrow(()->new ResponseStatusException(HttpStatus.NOT_FOUND));UserAccount user=p==null?null:support.user(p);boolean allowed=false;
        if("ITEM".equals(u.purpose)&&u.itemId!=null){allowed=items.findById(u.itemId).map(i->!"REMOVED".equals(i.status)).orElse(false);}
        if(user!=null){allowed|=u.owner.id.equals(user.id)||"ADMIN".equals(user.role);if(u.claimId!=null){Claim c=claims.findById(u.claimId).orElse(null);allowed|=c!=null&&(c.claimant.id.equals(user.id)||c.item.owner.id.equals(user.id));}}
        if(!allowed)throw new ResponseStatusException(HttpStatus.FORBIDDEN,"This image is private");Path path=Path.of(u.path).toAbsolutePath().normalize();if(!path.startsWith(root)||!Files.isRegularFile(path))throw new ResponseStatusException(HttpStatus.NOT_FOUND);
        return ResponseEntity.ok().contentType(MediaType.IMAGE_PNG).cacheControl(CacheControl.noStore()).header("X-Content-Type-Options","nosniff").body(Files.readAllBytes(path));
    }
    @DeleteMapping("/{id}") public Object remove(@PathVariable String id,Principal p)throws Exception {UserAccount user=support.user(p);Upload u=uploads.findById(id).orElseThrow();ItemService.check(u.owner.id.equals(user.id)&&u.claimId==null&&u.itemId==null&&!id.equals(user.profileImageId),"Only your unused uploads can be removed");Path path=Path.of(u.path).toAbsolutePath().normalize();if(path.startsWith(root))Files.deleteIfExists(path);uploads.delete(u);return Map.of("ok",true);}
}

"""Conservative cube-subset LOD with per-view silhouette and textured-image gates.
Every retained cube keeps its original UV, transform, parent and material.
"""
import copy
import numpy as np
from PIL import Image,ImageDraw
from render_part_icon import render_part_icon


def views():
    def rotate(axis,degrees):
        angle=np.radians(degrees);c,s=np.cos(angle),np.sin(angle);m=np.eye(3)
        a,b={'x':(1,2),'y':(0,2),'z':(0,1)}[axis];m[a,a]=m[b,b]=c;m[a,b]=-s;m[b,a]=s;return m
    return [np.eye(3),rotate('y',180),rotate('y',90),rotate('y',-90),rotate('x',90),rotate('x',-90),rotate('y',45)@rotate('x',20),rotate('y',-45)@rotate('x',-20)]


def simplify(bones,uv,texture,policy,cube_geometry):
    records=[]
    for name,bone in bones.items():
        for index,cube in enumerate(bone.get('cubes',[])):
            vertices,faces=cube_geometry(bone,cube,bones,np.eye(4))
            records.append((name,index,vertices,faces))
    if not records:raise ValueError('LOD entity has no geometry')
    count=len(records);size=policy.get('silhouetteResolution',96);preview_size=policy.get('textureResolution',48)
    directions=views();points=np.concatenate([r[2] for r in records]);lo=points.min(axis=0);hi=points.max(axis=0)
    # Exact outer bounds and at least one cube per animated source bone remain.
    protected=set()
    for axis in range(3):
        for target in (lo[axis],hi[axis]):
            protected.add(next(i for i,r in enumerate(records) if np.any(np.isclose(r[2][:,axis],target,atol=1e-9,rtol=0))))
    horizontal=np.array([.22,0,-.9755]);horizontal/=np.linalg.norm(horizontal);vertical=np.array([0,1.,0])
    masks=[];frames=[]
    for rotation in directions:
        transformed=points@rotation.T;projected=np.stack((transformed@horizontal,-transformed@vertical),axis=-1);low=projected.min(axis=0);high=projected.max(axis=0)
        scale=(size-12)/max(float((high-low).max()),1e-6);frames.append((rotation,transformed.min(axis=0),transformed.max(axis=0)))
        view_masks=[]
        for _,_,vertices,faces in records:
            v=vertices@rotation.T;screen=(np.stack((v@horizontal,-v@vertical),axis=-1)-(low+high)/2)*scale+size/2
            image=Image.new('1',(size,size));draw=ImageDraw.Draw(image)
            for ids,_ in faces:draw.polygon([tuple(p) for p in screen[ids]],fill=1)
            view_masks.append(np.asarray(image,dtype=np.uint8))
        masks.append(np.stack(view_masks))
    masks=np.stack(masks);coverage=masks.sum(axis=1,dtype=np.int32);reference=coverage>0;areas=reference.sum(axis=(1,2));retained=set(range(count));removed=[]
    bybone={name:sum(r[0]==name for r in records) for name in bones}
    # Small surface details first; silhouettes are checked against the complete source.
    order=sorted(range(count),key=lambda i:(int(masks[:,i].sum()),records[i][0],records[i][1]))
    for index in order:
        name=records[index][0]
        if index in protected or bybone[name]<=1:continue
        candidate=coverage-masks[:,index]
        loss=((reference & (candidate==0)).sum(axis=(1,2))/np.maximum(areas,1))
        if float(loss.max())>policy.get('maxSilhouetteLoss',.01):continue
        coverage=candidate;retained.remove(index);removed.append(index);bybone[name]-=1
    library={'materials':{'part':{'baseColor':'#ffffff','texture':'source','textureScale':1}}};bindings={'defaultMaterial':'part','parts':{}}
    def render(selected,frame):
        rotation,low,high=frame;triangles=[]
        for index in sorted(selected):
            _,_,vertices,faces=records[index];v=vertices@rotation.T
            for ids,coords in faces:triangles.append({'vertices':v[ids].tolist(),'uv':(np.asarray(coords)/uv).tolist(),'region':'part'})
        # Degenerate geometry fixes the framing to the high source in every comparison.
        for bound in (low,high):triangles.append({'vertices':[bound.tolist()]*3,'uv':[[0,0]]*3,'region':'part'})
        return render_part_icon({'definitionId':'part','meshes':[{'name':'part','triangles':triangles}]},library,bindings,lambda _:texture,size=preview_size,muzzle_left=True,alpha_cutout=True)
    high_images=[render(set(range(count)),f) for f in frames]
    def compare(selected):
        images=[render(selected,f) for f in frames];metrics=[]
        for high_image,low_image in zip(high_images,images):
            high=np.asarray(high_image,dtype=float)/255;low=np.asarray(low_image,dtype=float)/255
            hp=high[:,:,:3]*high[:,:,3:]+.2*(1-high[:,:,3:]);lp=low[:,:,:3]*low[:,:,3:]+.2*(1-low[:,:,3:])
            foreground=(high[:,:,3]>.05)|(low[:,:,3]>.05);difference=np.max(np.abs(hp-lp),axis=2)
            metrics.append({'meanError':float(difference[foreground].mean()) if foreground.any() else 0,'changedPixelFraction':float((difference[foreground]>.1).mean()) if foreground.any() else 0})
        valid=all(m['meanError']<=policy.get('maxTextureMeanError',.02) and m['changedPixelFraction']<=policy.get('maxTextureChangedFraction',.05) for m in metrics)
        return valid,metrics,images
    # If texture/depth changes reveal holes, progressively restore the removed details.
    valid,metrics,low_images=compare(retained)
    while not valid and removed:
        restore=max(1,len(removed)//2)
        for index in removed[-restore:]:retained.add(index)
        removed=removed[:-restore];valid,metrics,low_images=compare(retained)
    assert valid
    selection={name:[r[1] for i,r in enumerate(records) if r[0]==name and i in retained] for name in bones if bones[name].get('cubes')}
    silhouette=(reference & (masks[:,sorted(retained)].sum(axis=1)==0)).sum(axis=(1,2))/np.maximum(areas,1)
    evidence={'highCubes':count,'lowCubes':len(retained),'highTriangles':sum(len(r[3]) for r in records),'lowTriangles':sum(len(records[i][3]) for i in retained),'selectedIndices':selection,'silhouetteLoss':silhouette.tolist(),'textureComparisons':metrics,'textureResolution':preview_size,'preserve':'original cube transform/UV, every source bone represented, exact axis bounds'}
    return selection,evidence,high_images,low_images

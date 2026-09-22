<?php
namespace App\Models;
use App\Models\Concerns\GeneratesHexId;
use Illuminate\Database\Eloquent\Model;
class Complaint extends Model
{
 use GeneratesHexId; protected $fillable=['user_id','order_number','customer_name','customer_phone','message','image_url','status','admin_note']; public function user(){return $this->belongsTo(User::class);}
}

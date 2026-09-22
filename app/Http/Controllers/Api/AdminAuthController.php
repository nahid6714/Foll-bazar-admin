<?php
namespace App\Http\Controllers\Api;

use App\Http\Requests\LoginRequest;
use App\Models\User;
use Illuminate\Support\Facades\Hash;
use Illuminate\Validation\ValidationException;

class AdminAuthController extends BaseApiController
{
    public function login(LoginRequest $r)
    {
        $identifier = trim($r->identifier);
        $email = str_contains($identifier, '@') ? strtolower($identifier) : 'phone_'.preg_replace('/\D+/', '', $identifier).'@customer.falbazar.local';
        $user = User::where('email', $email)->first();
        if (!$user || $user->role !== 'admin' || !Hash::check($r->password, $user->password_hash)) {
            throw ValidationException::withMessages(['identifier' => ['Invalid admin credentials']]);
        }
        $token = $user->createToken('admin-app')->plainTextToken;
        return $this->ok([
            'access_token' => $token,
            'user' => [
                'id' => $user->id,
                'email' => $user->email,
                'role' => $user->role,
                'name' => $user->profile?->full_name ?: 'Fol Bazar Admin',
            ],
        ]);
    }
}
